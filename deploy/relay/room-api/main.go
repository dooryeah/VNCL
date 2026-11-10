// VNCL room service.
//
// Creates short-lived STCP room descriptors for the VNCL launcher. The service deliberately
// stores no Minecraft credentials: a room is just a code, an frp proxy name, a shared secret,
// and a host token used for heartbeat/deletion.
package main

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"log"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

const codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

type room struct {
	Code      string
	ProxyName string
	Secret    string
	HostToken string
	ExpiresAt time.Time
}

type server struct {
	mu       sync.Mutex
	rooms    map[string]*room
	maxRooms int
	ttl      time.Duration
	// rate[ip] = timestamps of recent join/create attempts.
	rate map[string][]time.Time
}

func newServer(maxRooms int, ttl time.Duration) *server {
	s := &server{
		rooms:    make(map[string]*room),
		maxRooms: maxRooms,
		ttl:      ttl,
		rate:     make(map[string][]time.Time),
	}
	go s.cleanupLoop()
	return s
}

func (s *server) cleanupLoop() {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	for range ticker.C {
		now := time.Now()
		s.mu.Lock()
		for code, r := range s.rooms {
			if now.After(r.ExpiresAt) {
				delete(s.rooms, code)
			}
		}
		for ip, times := range s.rate {
			kept := times[:0]
			for _, t := range times {
				if now.Sub(t) < time.Minute {
					kept = append(kept, t)
				}
			}
			if len(kept) == 0 {
				delete(s.rate, ip)
			} else {
				s.rate[ip] = kept
			}
		}
		s.mu.Unlock()
	}
}

func (s *server) allow(ip string, limit int) bool {
	now := time.Now()
	times := s.rate[ip]
	kept := times[:0]
	for _, t := range times {
		if now.Sub(t) < time.Minute {
			kept = append(kept, t)
		}
	}
	if len(kept) >= limit {
		s.rate[ip] = kept
		return false
	}
	s.rate[ip] = append(kept, now)
	return true
}

func randomString(n int, alphabet string) string {
	out := make([]byte, n)
	for i := range out {
		b := make([]byte, 1)
		if _, err := rand.Read(b); err != nil {
			panic(err)
		}
		out[i] = alphabet[int(b[0])%len(alphabet)]
	}
	return string(out)
}

func randomSecret() string {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return base64.RawURLEncoding.EncodeToString(b)
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func clientIP(r *http.Request) string {
	if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
		return strings.TrimSpace(strings.Split(forwarded, ",")[0])
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

func hostToken(r *http.Request) string {
	header := r.Header.Get("Authorization")
	if strings.HasPrefix(header, "Bearer ") {
		return strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
	}
	return ""
}

func (s *server) create(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !s.allow(clientIP(r), 5) {
		writeError(w, http.StatusTooManyRequests, "too many room creations")
		return
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	if len(s.rooms) >= s.maxRooms {
		writeError(w, http.StatusServiceUnavailable, "room capacity reached")
		return
	}

	code := ""
	for {
		code = randomString(6, codeAlphabet)
		if _, exists := s.rooms[code]; !exists {
			break
		}
	}
	now := time.Now()
	created := &room{
		Code:      code,
		ProxyName: "vncl-" + code,
		Secret:    randomSecret(),
		HostToken: randomSecret(),
		ExpiresAt: now.Add(s.ttl),
	}
	s.rooms[code] = created
	writeJSON(w, http.StatusCreated, map[string]any{
		"code":      created.Code,
		"proxyName": created.ProxyName,
		"secret":    created.Secret,
		"hostToken": created.HostToken,
		"expiresAt": created.ExpiresAt.Unix(),
	})
}

func (s *server) join(w http.ResponseWriter, r *http.Request, code string) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	if !s.allow(clientIP(r), 30) {
		writeError(w, http.StatusTooManyRequests, "too many join attempts")
		return
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	room, ok := s.rooms[strings.ToUpper(code)]
	if !ok || time.Now().After(room.ExpiresAt) {
		writeError(w, http.StatusNotFound, "room not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"code":      room.Code,
		"proxyName": room.ProxyName,
		"secret":    room.Secret,
		"expiresAt": room.ExpiresAt.Unix(),
	})
}

func (s *server) heartbeat(w http.ResponseWriter, r *http.Request, code string) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	room, ok := s.rooms[strings.ToUpper(code)]
	if !ok || room.HostToken != hostToken(r) {
		writeError(w, http.StatusNotFound, "room not found")
		return
	}
	room.ExpiresAt = time.Now().Add(s.ttl)
	writeJSON(w, http.StatusOK, map[string]any{"expiresAt": room.ExpiresAt.Unix()})
}

func (s *server) delete(w http.ResponseWriter, r *http.Request, code string) {
	if r.Method != http.MethodDelete {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	room, ok := s.rooms[strings.ToUpper(code)]
	if !ok || room.HostToken != hostToken(r) {
		writeError(w, http.StatusNotFound, "room not found")
		return
	}
	delete(s.rooms, room.Code)
	w.WriteHeader(http.StatusNoContent)
}

func (s *server) handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("/api/rooms", s.create)
	mux.HandleFunc("/api/rooms/", func(w http.ResponseWriter, r *http.Request) {
		rest := strings.TrimPrefix(r.URL.Path, "/api/rooms/")
		parts := strings.Split(rest, "/")
		code := strings.ToUpper(parts[0])
		if len(parts) == 1 {
			if r.Method == http.MethodDelete {
				s.delete(w, r, code)
			} else {
				s.join(w, r, code)
			}
			return
		}
		if len(parts) == 2 && parts[1] == "heartbeat" {
			s.heartbeat(w, r, code)
			return
		}
		if len(parts) == 2 && parts[1] == "delete" {
			s.delete(w, r, code)
			return
		}
		writeError(w, http.StatusNotFound, "not found")
	})
	return mux
}

func main() {
	addr := os.Getenv("ADDR")
	if addr == "" {
		addr = "127.0.0.1:8080"
	}
	maxRooms := 10
	if value := os.Getenv("MAX_ROOMS"); value != "" {
		if parsed, err := strconv.Atoi(value); err == nil && parsed > 0 {
			maxRooms = parsed
		}
	}
	ttlHours := 12
	if value := os.Getenv("TTL_HOURS"); value != "" {
		if parsed, err := strconv.Atoi(value); err == nil && parsed > 0 {
			ttlHours = parsed
		}
	}
	s := newServer(maxRooms, time.Duration(ttlHours)*time.Hour)
	log.Printf("VNCL room service listening on %s (maxRooms=%d, ttl=%dh)", addr, maxRooms, ttlHours)
	if err := http.ListenAndServe(addr, s.handler()); err != nil {
		log.Fatal(err)
	}
}
