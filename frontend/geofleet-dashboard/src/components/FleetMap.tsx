import { useEffect, useState, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { Vehicle } from '@/types/fleet';
import { formatDistanceToNow } from 'date-fns';
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

interface GeoFenceDTO {
  id: number;
  name: string;
  polygonGeojson: string;
  coordinates: number[][][]; // [[[lng, lat], ...]]
}

interface FleetMapProps {
  vehicles: Vehicle[];
  highlightedVehicleId: string | null;
  onVehicleSelect: (vehicleId: string | null) => void;
}

// Fix Leaflet default marker icons
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Custom vehicle icon - Vibrant colors for dark theme
const createVehicleIcon = (status: 'online' | 'idle' | 'offline', heading: number, isHighlighted: boolean) => {
  const colors = {
    online: '#10b981',   // Emerald green
    idle: '#f59e0b',     // Amber
    offline: '#ef4444',  // Red
  };

  const color = colors[status];
  const size = isHighlighted ? 52 : 42;
  const glowSize = isHighlighted ? 16 : 10;

  return L.divIcon({
    className: 'vehicle-marker',
    html: `
      <div style="position: relative; filter: drop-shadow(0 0 ${glowSize}px ${color});">
        <svg width="${size}" height="${size}" viewBox="0 0 36 36" xmlns="http://www.w3.org/2000/svg" style="transform: rotate(${heading}deg);">
          <!-- Outer glow -->
          <path d="M18 8 L26 28 L18 24 L10 28 Z" fill="${color}" stroke="${color}" stroke-width="1" opacity="0.3" transform="scale(1.1) translate(-1.8, -1.8)"/>
          <!-- Main arrow with white outline -->
          <path d="M18 8 L26 28 L18 24 L10 28 Z" fill="${color}" stroke="rgba(255,255,255,0.9)" stroke-width="2"/>
          <!-- Inner highlight -->
          <path d="M18 12 L23 25 L18 22 L13 25 Z" fill="${color}" opacity="0.8"/>
          <!-- Center dot -->
          <circle cx="18" cy="18" r="3" fill="white" opacity="0.9"/>
        </svg>
        ${isHighlighted ? `
          <div style="position: absolute; inset: -12px; border: 3px solid ${color}; border-radius: 50%; animation: pulse-ring 1.5s infinite; box-shadow: 0 0 20px ${color};"></div>
        ` : ''}
      </div>
      <style>
        @keyframes pulse-ring {
          0% { transform: scale(1); opacity: 1; }
          70% { transform: scale(1.5); opacity: 0; }
          100% { transform: scale(1.5); opacity: 0; }
        }
      </style>
    `,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
};

function animateMarker(marker: L.Marker, from: L.LatLng, to: L.LatLng, duration: number = 600) {
  const startTime = performance.now();
  const animate = (time: number) => {
    const progress = Math.min((time - startTime) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    const lat = from.lat + (to.lat - from.lat) * eased;
    const lng = from.lng + (to.lng - from.lng) * eased;
    marker.setLatLng([lat, lng]);
    if (progress < 1) requestAnimationFrame(animate);
  };
  requestAnimationFrame(animate);
}

export function FleetMap({ vehicles, highlightedVehicleId, onVehicleSelect }: FleetMapProps) {
  const mapRef = useRef<L.Map | null>(null);
  const markersRef = useRef<Map<string, L.Marker>>(new Map());
  const polygonsRef = useRef<Map<number, L.Polygon>>(new Map());
  const containerRef = useRef<HTMLDivElement>(null);
  const [isMapReady, setIsMapReady] = useState(false);
  const [geofences, setGeofences] = useState<GeoFenceDTO[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    setIsLoading(true);
    fetch(`${API_BASE_URL}/geofences/geojson`)
      .then(res => {
        if (!res.ok) {
          console.error('Geofences fetch failed:', res.status, res.statusText);
          throw new Error(`HTTP ${res.status}`);
        }
        return res.json();
      })
      .then((data: GeoFenceDTO[]) => {
        console.log('✅ Geofences successfully loaded:', data);
        setGeofences(data);
      })
      .catch(err => {
        console.error('Failed to load geofences:', err);
      })
      .finally(() => setIsLoading(false));
  }, []);

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;

    const center: [number, number] = vehicles.length > 0
      ? [
        vehicles.reduce((a, v) => a + v.lat, 0) / vehicles.length,
        vehicles.reduce((a, v) => a + v.lng, 0) / vehicles.length,
      ]
      : [24.8607, 67.0011];

    const map = L.map(containerRef.current, {
      center,
      zoom: 12,
      zoomControl: false,
      preferCanvas: true,
    });

    // Perfect match for your Karachi map + dark sidebar
    L.tileLayer('https://{s}.google.com/vt/lyrs=s,h&x={x}&y={y}&z={z}', {
      maxZoom: 20,
      subdomains: ['mt0', 'mt1', 'mt2', 'mt3'],
      attribution: `
    &copy; <a href="https://www.google.com/maps" target="_blank">Google Maps</a> |
    <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors
  `,
    }).addTo(map);
    L.control.zoom({ position: 'topright' }).addTo(map);
    L.control.scale({ position: 'bottomleft', imperial: false }).addTo(map);

    mapRef.current = map;
    setIsMapReady(true);

    return () => {
      map.remove();
      mapRef.current = null;
      markersRef.current.clear();
      polygonsRef.current.clear();
    };
  }, []);

  useEffect(() => {
    if (!mapRef.current || !isMapReady) return;

    const vehicleIds = new Set(vehicles.map(v => v.vehicleId));

    markersRef.current.forEach((marker, id) => {
      if (!vehicleIds.has(id)) {
        marker.remove();
        markersRef.current.delete(id);
      }
    });

    vehicles.forEach(vehicle => {
      const isHighlighted = vehicle.vehicleId === highlightedVehicleId;
      const icon = createVehicleIcon(vehicle.status || 'online', vehicle.heading, isHighlighted);
      const latLng = L.latLng(vehicle.lat, vehicle.lng);

      if (markersRef.current.has(vehicle.vehicleId)) {
        const marker = markersRef.current.get(vehicle.vehicleId)!;
        animateMarker(marker, marker.getLatLng(), latLng);
        marker.setIcon(icon);
        marker.setPopupContent(createPopupContent(vehicle));
      } else {
        const marker = L.marker(latLng, { icon })
          .addTo(mapRef.current!)
          .on('click', () => onVehicleSelect(vehicle.vehicleId));

        marker.bindPopup(createPopupContent(vehicle), {
          className: 'vehicle-popup',
          maxWidth: 300,
        });

        markersRef.current.set(vehicle.vehicleId, marker);
      }
    });
  }, [vehicles, highlightedVehicleId, isMapReady, onVehicleSelect]);

  useEffect(() => {
    if (!mapRef.current || !isMapReady || isLoading) return;

    const ids = new Set(geofences.map(g => g.id));
    polygonsRef.current.forEach((poly, id) => {
      if (!ids.has(id)) {
        poly.remove();
        polygonsRef.current.delete(id);
      }
    });

    geofences.forEach(fence => {
      if (fence.coordinates?.[0]?.length >= 3) {
        const latLngs = fence.coordinates[0].map(([lng, lat]) => [lat, lng] as [number, number]);

        if (polygonsRef.current.has(fence.id)) {
          polygonsRef.current.get(fence.id)!.setLatLngs(latLngs);
        } else {
          const polygon = L.polygon(latLngs, {
            color: '#9333ea',        // Purple-600
            weight: 3,
            opacity: 0.9,
            fillColor: '#c084fc',
            fillOpacity: 0.15,
            className: 'geofence-polygon',
          })
            .addTo(mapRef.current!)
            .bindTooltip(fence.name, {
              permanent: true,
              direction: 'center',
              className: 'geofence-label',
              offset: [0, 0],
            });

          polygon.on('mouseover', () => polygon.setStyle({ fillOpacity: 0.3, weight: 4 })).bringToFront();
          polygon.on('mouseout', () => polygon.setStyle({ fillOpacity: 0.15, weight: 3 }));

          polygonsRef.current.set(fence.id, polygon);
        }
      }
    });
  }, [geofences, isMapReady, isLoading]);

  useEffect(() => {
    if (!mapRef.current || !highlightedVehicleId) return;
    const vehicle = vehicles.find(v => v.vehicleId === highlightedVehicleId);
    if (vehicle) {
      mapRef.current.flyTo([vehicle.lat, vehicle.lng], 16, { duration: 1 });
      const marker = markersRef.current.get(highlightedVehicleId);
      if (marker) setTimeout(() => marker.openPopup(), 1000);
    }
  }, [highlightedVehicleId, vehicles]);

  return (
    <div className="relative w-full h-full rounded-2xl overflow-hidden border border-border/50 bg-card shadow-lg">
      <div ref={containerRef} className="w-full h-full" />

      {isLoading && (
        <div className="absolute inset-0 bg-background/80 backdrop-blur-sm z-[1000] flex items-center justify-center">
          <div className="text-center">
            <div className="w-16 h-16 border-4 border-primary/30 border-t-primary rounded-full animate-spin mx-auto mb-4"></div>
            <p className="text-foreground font-medium">Loading geofences...</p>
          </div>
        </div>
      )}

      {/* Legend - Professional Dark Theme */}
      <div className="absolute bottom-6 left-6 bg-card/95 backdrop-blur-xl border border-border/50 p-5 rounded-2xl z-[1000] shadow-2xl max-w-[260px]">
        <div className="space-y-5">
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">Vehicle Status</p>
            <div className="space-y-3">
              {[
                { status: 'online', color: '#10b981', label: 'Online' },
                { status: 'idle', color: '#f59e0b', label: 'Idle' },
                { status: 'offline', color: '#ef4444', label: 'Offline' },
              ].map(({ status, color, label }) => (
                <div key={label} className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-3 h-3 rounded-full shadow-lg" style={{ backgroundColor: color, boxShadow: `0 0 10px ${color}50` }} />
                    <span className="text-sm text-foreground">{label}</span>
                  </div>
                  <span className="text-sm font-bold text-foreground">
                    {vehicles.filter(v => v.status === status).length}
                  </span>
                </div>
              ))}
            </div>
          </div>

          <div className="pt-4 border-t border-border/50">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-3">
                <div className="w-4 h-4 rounded border-2 border-purple-500 bg-purple-500/20" />
                <span className="text-sm text-foreground">Geofences</span>
              </div>
              <span className="text-sm font-bold text-foreground">{geofences.length}</span>
            </div>
          </div>

          <div className="pt-4 border-t border-border/50 grid grid-cols-2 gap-4">
            <div className="text-center p-3 rounded-xl bg-muted/30">
              <div className="text-2xl font-bold text-foreground">{vehicles.length}</div>
              <div className="text-xs text-muted-foreground mt-1">Total Fleet</div>
            </div>
            <div className="text-center p-3 rounded-xl bg-primary/10 border border-primary/20">
              <div className="text-2xl font-bold text-primary">
                {vehicles.filter(v => v.status === 'online').length}
              </div>
              <div className="text-xs text-muted-foreground mt-1">Active Now</div>
            </div>
          </div>
        </div>
      </div>

      <div className="absolute top-6 right-6 bg-card/95 backdrop-blur-xl border border-border/50 px-4 py-3 rounded-xl z-[1000] shadow-lg">
        <p className="text-sm text-muted-foreground">
          Use <kbd className="px-2 py-1 bg-muted rounded text-xs font-mono text-foreground">Scroll</kbd> to zoom
        </p>
      </div>
    </div>
  );
}

function createPopupContent(vehicle: Vehicle): string {
  const colors = {
    online: '#10b981',
    idle: '#f59e0b',
    offline: '#ef4444',
  };
  const status = vehicle.status || 'online';
  const color = colors[status];
  const lastSeen = formatDistanceToNow(new Date(vehicle.timestamp), { addSuffix: true });

  return `
    <div style="font-family: 'Inter', system-ui, sans-serif; padding: 20px; min-width: 280px; background: linear-gradient(180deg, hsl(217 33% 19%) 0%, hsl(217 33% 15%) 100%); border-radius: 16px; box-shadow: 0 25px 50px rgba(0,0,0,0.5);">
      <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; padding-bottom: 16px; border-bottom: 1px solid rgba(255,255,255,0.1);">
        <strong style="font-size: 18px; color: #f8fafc; font-weight: 700;">${vehicle.vehicleId}</strong>
        <span style="background: ${color}; color: white; padding: 6px 14px; border-radius: 9999px; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; box-shadow: 0 0 15px ${color}50;">
          ${status}
        </span>
      </div>

      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 16px 0;">
        <div style="background: rgba(255,255,255,0.05); padding: 14px; border-radius: 12px;">
          <div style="font-size: 11px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.1em; margin-bottom: 6px;">Speed</div>
          <div style="font-size: 24px; font-weight: 700; color: #f8fafc;">
            ${Math.round(vehicle.speedKph)} <span style="font-size: 14px; font-weight: 500; color: #94a3b8;">km/h</span>
          </div>
        </div>
        <div style="background: rgba(255,255,255,0.05); padding: 14px; border-radius: 12px;">
          <div style="font-size: 11px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.1em; margin-bottom: 6px;">Heading</div>
          <div style="font-size: 24px; font-weight: 700; color: #f8fafc;">
            ${Math.round(vehicle.heading)}°
          </div>
        </div>
      </div>

      <div style="background: rgba(20, 184, 166, 0.1); border: 1px solid rgba(20, 184, 166, 0.2); padding: 14px; border-radius: 12px; margin-bottom: 12px;">
        <div style="font-size: 11px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.1em; margin-bottom: 4px;">Last Updated</div>
        <div style="font-size: 15px; font-weight: 600; color: #14b8a6;">${lastSeen}</div>
      </div>

      ${vehicle.region ? `
        <div style="display: flex; align-items: center; gap: 10px; padding-top: 12px; border-top: 1px solid rgba(255,255,255,0.1);">
          <span style="font-size: 18px;">📍</span>
          <span style="font-size: 13px; color: #94a3b8;">Region:</span>
          <span style="font-size: 14px; font-weight: 600; color: #f8fafc; margin-left: auto;">${vehicle.region}</span>
        </div>
      ` : ''}
    </div>
  `;
}