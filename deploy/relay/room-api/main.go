// VNCL room service and frps HTTP auth plugin.
//
// Creates short-lived STCP room descriptors for the VNCL launcher. The same process also answers
// frps' HTTP plugin calls so frpc logins are authenticated per room instead of with a global token.
package main

import (
	"crypto/rand"
	"crypto/subtle"
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
	Code       string
	ProxyName  string
	Secret     string
	HostToken  string
	GuestToken string
	ExpiresAt  time.Time
}

type server struct {
	mu        sync.Mutex
	rooms     map[string]*room
	maxRooms  int
	ttl       time.Duration
	frpServer string
	frpPort   int
	// rate[ip] = timestamps of recent join/create attempts.
	rate map[string][]time.Time
}

func newServer(maxRooms int, ttl time.Duration, frpServer string, frpPort int) *server {
	s := &server{
		rooms:     make(map[string]*room),
		maxRooms:  maxRooms,
		ttl:       ttl,
		frpServer: frpServer,
		frpPort:   frpPort,
		rate:      make(map[string][]time.Time),
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

func bearer(r *http.Request) string {
	header := r.Header.Get("Authorization")
	if strings.HasPrefix(header, "Bearer ") {
		return strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
	}
	return ""
}

func (s *server) findRoomLocked(code string) *room {
	r, ok := s.rooms[strings.ToUpper(code)]
	if !ok || time.Now().After(r.ExpiresAt) {
		return nil
	}
	return r
}

// roomFromUser resolves "vncl-CODE-host" or "vncl-CODE-guest" to a live room.
func (s *server) roomFromUser(user string) (*room, string, bool) {
	const prefix = "vncl-"
	const hostSuffix = "-host"
	const guestSuffix = "-guest"
	if !strings.HasPrefix(strings.ToLower(user), prefix) {
		return nil, "", false
	}
	role := ""
	code := ""
	if strings.HasSuffix(user, hostSuffix) {
		role = "host"
		code = strings.TrimSuffix(strings.TrimPrefix(user, prefix), hostSuffix)
	} else if strings.HasSuffix(user, guestSuffix) {
		role = "guest"
		code = strings.TrimSuffix(strings.TrimPrefix(user, prefix), guestSuffix)
	} else {
		return nil, "", false
	}
	room := s.findRoomLocked(code)
	if room == nil {
		return nil, "", false
	}
	return room, role, true
}

func constantTimeEqual(a, b string) bool {
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
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
		Code:       code,
		ProxyName:  "vncl-" + code,
		Secret:     randomSecret(),
		HostToken:  randomSecret(),
		GuestToken: randomSecret(),
		ExpiresAt:  now.Add(s.ttl),
	}
	s.rooms[code] = created
	writeJSON(w, http.StatusCreated, map[string]any{
		"code":      created.Code,
		"proxyName": created.ProxyName,
		"secret":    created.Secret,
		"hostToken": created.HostToken,
		"frpServer": s.frpServer,
		"frpPort":   s.frpPort,
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
	room := s.findRoomLocked(code)
	if room == nil {
		writeError(w, http.StatusNotFound, "room not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"code":       room.Code,
		"proxyName":  room.ProxyName,
		"secret":     room.Secret,
		"guestToken": room.GuestToken,
		"frpServer":  s.frpServer,
		"frpPort":    s.frpPort,
		"expiresAt":  room.ExpiresAt.Unix(),
	})
}

func (s *server) heartbeat(w http.ResponseWriter, r *http.Request, code string) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	room := s.findRoomLocked(code)
	if room == nil || !constantTimeEqual(room.HostToken, bearer(r)) {
		writeError(w, http.StatusNotFound, "room not found")
		return
	}
	room.ExpiresAt = time.Now().Add(s.ttl)
	writeJSON(w, http.StatusOK, map[string]any{"expiresAt": room.ExpiresAt.Unix()})
}

func (s *server) remove(w http.ResponseWriter, r *http.Request, code string) {
	if r.Method != http.MethodDelete {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	room := s.findRoomLocked(code)
	if room == nil || !constantTimeEqual(room.HostToken, bearer(r)) {
		writeError(w, http.StatusNotFound, "room not found")
		return
	}
	delete(s.rooms, room.Code)
	w.WriteHeader(http.StatusNoContent)
}

type pluginRequest struct {
	Version string          `json:"version"`
	Op      string          `json:"op"`
	Content json.RawMessage `json:"content"`
}

type pluginUser struct {
	User  string            `json:"user"`
	Metas map[string]string `json:"metas"`
	RunID string            `json:"run_id"`
}

type loginContent struct {
	User  string            `json:"user"`
	Metas map[string]string `json:"metas"`
}

type proxyContent struct {
	User      pluginUser `json:"user"`
	ProxyName string     `json:"proxy_name"`
	ProxyType string     `json:"proxy_type"`
	SK        string     `json:"sk"`
}

func pluginAllow(w http.ResponseWriter) {
	writeJSON(w, http.StatusOK, map[string]any{"reject": false, "unchange": true})
}

func pluginReject(w http.ResponseWriter, reason string) {
	writeJSON(w, http.StatusOK, map[string]any{"reject": true, "reject_reason": reason})
}

func (s *server) plugin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.NotFound(w, r)
		return
	}
	var request pluginRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		pluginReject(w, "invalid request")
		return
	}

	switch strings.ToLower(request.Op) {
	case "login":
		var content loginContent
		if err := json.Unmarshal(request.Content, &content); err != nil {
			pluginReject(w, "invalid login")
			return
		}
		s.mu.Lock()
		room, role, ok := s.roomFromUser(content.User)
		s.mu.Unlock()
		if !ok {
			pluginReject(w, "unknown room")
			return
		}
		token := content.Metas["token"]
		expected := room.HostToken
		if role == "guest" {
			expected = room.GuestToken
		}
		if !constantTimeEqual(token, expected) {
			pluginReject(w, "invalid room token")
			return
		}
		pluginAllow(w)
	case "newproxy":
		var content proxyContent
		if err := json.Unmarshal(request.Content, &content); err != nil {
			pluginReject(w, "invalid proxy")
			return
		}
		s.mu.Lock()
		room, role, ok := s.roomFromUser(content.User.User)
		s.mu.Unlock()
		if !ok || role != "host" {
			pluginReject(w, "only the room host may create proxies")
			return
		}
		log.Printf("plugin NewProxy user=%q proxy=%q type=%q hasSecret=%t", content.User.User, content.ProxyName, content.ProxyType, content.SK != "")
		proxyNameOK := content.ProxyName == room.ProxyName || strings.HasSuffix(content.ProxyName, "."+room.ProxyName)
		if !proxyNameOK || content.ProxyType != "stcp" {
			pluginReject(w, "invalid room proxy")
			return
		}
		if content.SK != "" && !constantTimeEqual(content.SK, room.Secret) {
			pluginReject(w, "invalid room secret")
			return
		}
		pluginAllow(w)
	case "newuserconn", "ping", "newworkconn", "closeproxy":
		var content struct {
			User pluginUser `json:"user"`
		}
		if err := json.Unmarshal(request.Content, &content); err != nil {
			pluginReject(w, "invalid operation")
			return
		}
		s.mu.Lock()
		_, _, ok := s.roomFromUser(content.User.User)
		s.mu.Unlock()
		if !ok {
			pluginReject(w, "unknown room")
			return
		}
		pluginAllow(w)
	default:
		pluginReject(w, "unsupported operation")
	}
}

func (s *server) handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("/plugin", s.plugin)
	mux.HandleFunc("/api/rooms", s.create)
	mux.HandleFunc("/api/rooms/", func(w http.ResponseWriter, r *http.Request) {
		rest := strings.TrimPrefix(r.URL.Path, "/api/rooms/")
		parts := strings.Split(rest, "/")
		code := strings.ToUpper(parts[0])
		if len(parts) == 1 {
			if r.Method == http.MethodDelete {
				s.remove(w, r, code)
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
			s.remove(w, r, code)
			return
		}
		writeError(w, http.StatusNotFound, "not found")
	})
	return mux
}

func envInt(name string, fallback int) int {
	if value := os.Getenv(name); value != "" {
		if parsed, err := strconv.Atoi(value); err == nil && parsed > 0 {
			return parsed
		}
	}
	return fallback
}

func main() {
	addr := os.Getenv("ADDR")
	if addr == "" {
		addr = "127.0.0.1:8080"
	}
	frpServer := os.Getenv("FRP_SERVER")
	if frpServer == "" {
		frpServer = "47.98.250.11"
	}
	maxRooms := envInt("MAX_ROOMS", 10)
	ttlHours := envInt("TTL_HOURS", 12)
	frpPort := envInt("FRP_PORT", 7000)

	s := newServer(maxRooms, time.Duration(ttlHours)*time.Hour, frpServer, frpPort)
	log.Printf("VNCL room service listening on %s (maxRooms=%d, ttl=%dh, frp=%s:%d)",
		addr, maxRooms, ttlHours, frpServer, frpPort)
	if err := http.ListenAndServe(addr, s.handler()); err != nil {
		log.Fatal(err)
	}
}
