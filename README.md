FleetTrack – Real-Time Fleet Tracking & Geo-Event Alert System
FleetTrack Dashboard
A complete, end-to-end real-time fleet tracking platform built with Spring Boot, Kafka Streams, PostgreSQL + PostGIS, React + Leaflet, and Docker.
Live vehicle positions, instant alerts for speeding, idle time, and geofence entry/exit — all updated in real-time via Server-Sent Events (SSE).
Features

Real-time vehicle tracking on an interactive map (Leaflet + Google Satellite tiles)
Animated directional vehicle markers with heading rotation and status colors (Online / Idle / Offline)
Geofence support – polygons with labels, hover effects, entry/exit alerts
Instant alerts (Speeding, Idle >10 min, Geofence Enter/Exit)
Vehicle list with search, region filter, and status indicators
Stats bar – total vehicles, online count, alerts last hour, average speed
Live connection indicator and heartbeat support
Realistic vehicle simulator with Karachi routes, variable speeds, and long idles
Full Docker stack (Backend, Frontend, Kafka, Zookeeper, PostGIS, Prometheus)

Tech Stack
Backend

Java 17 + Spring Boot 3.1.5
Spring Kafka + Kafka Streams (stateful processing)
Spring Data JPA + Hibernate Spatial
PostgreSQL + PostGIS (spatial queries with ST_Covers)
Flyway migrations
Micrometer + Prometheus metrics
Lombok, MapStruct

Frontend

React 18 + TypeScript
Vite
Leaflet (custom SVG markers + animations)
Tailwind CSS + Shadcn UI components
Lucide icons
Custom SSE hooks with reconnection and mock mode

DevOps

Docker + Docker Compose
Multi-service stack: postgis, zookeeper, kafka, backend, frontend, prometheus

Project Structure
GEOFLEET/
├── .vscode/                     # VS Code settings (optional, good to keep)
├── backend/
│   └── tracking/                # Your Spring Boot source code
│       ├── mvn/                 # Maven wrapper
│       ├── kafka/               # Kafka config & topic init script
│       ├── postgres/            # Postgres init.sql
│       ├── src/
│       ├── target/              # Build output (ignored by git)
│       ├── .gitattributes
│       ├── .gitignore
│       ├── Dockerfile
│       ├── HELP.md
│       ├── mvnw / mvnw.cmd      # Maven wrapper scripts
│       └── pom.xml
│
├── frontend/                    # Your React + Vite dashboard
│   └── .gitignore
│
├── docker-compose.yml           ← At root → perfect!
├── prometheus.yml               ← At root → perfect!
├── .gitignore                   ← Root gitignore
└── README.md                    

Quick Start (Docker)

Clone the repositoryBashgit clone https://github.com/yourusername/fleet-tracking-project.git
cd fleet-tracking-project
Start the entire stackBashdocker compose up --build
Open the dashboard
http://localhost:8081

Services:

Backend: http://localhost:8080
Prometheus: http://localhost:9090
Frontend: http://localhost:8081

Manual Run (Development)
Backend
Bashcd backend
./mvnw spring-boot:run
Frontend
Bashcd frontend
npm install
npm run dev
Environment Variables (Frontend)
Create .env in frontend/:
envVITE_API_BASE_URL=http://localhost:8080/api
VITE_SSE_URL=http://localhost:8080/api/stream
VITE_USE_MOCK_DATA=false
Set VITE_USE_MOCK_DATA=true for offline demo with simulated data.

Future Enhancements

Alert cooldown (suppress duplicate speeding alerts)
Full DLQ routing for failed messages
Authentication & role-based access
Historical playback / trails
Mobile-responsive improvements

License
MIT License – feel free to use and modify.