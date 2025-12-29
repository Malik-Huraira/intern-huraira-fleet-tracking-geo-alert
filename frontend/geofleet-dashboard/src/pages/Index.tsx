import { useState, useCallback } from 'react';
import { Truck, AlertTriangle, Gauge, Clock, Zap, MapPin, Search, ChevronRight, Activity, Radio, Shield } from 'lucide-react';
import { FleetMap } from '@/components/FleetMap';
import { useVehicleStream, useAlertStream } from '@/hooks/useFleetStream';
import { Vehicle, Alert } from '@/types/fleet';
import { formatDistanceToNow } from 'date-fns';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';

const Index = () => {
  const { vehicles, isConnected: vehiclesConnected } = useVehicleStream();
  const { alerts, isConnected: alertsConnected } = useAlertStream();
  const [highlightedVehicleId, setHighlightedVehicleId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const handleAlertClick = useCallback((vehicleId: string) => {
    setHighlightedVehicleId(vehicleId);
  }, []);

  const handleVehicleSelect = useCallback((vehicleId: string) => {
    setHighlightedVehicleId(prev => prev === vehicleId ? null : vehicleId);
  }, []);

  const isConnected = vehiclesConnected && alertsConnected;
  const filteredVehicles = vehicles.filter(v => v.vehicleId.toLowerCase().includes(searchQuery.toLowerCase()));
  const onlineCount = vehicles.filter(v => v.status === 'online').length;
  const idleCount = vehicles.filter(v => v.status === 'idle').length;
  const offlineCount = vehicles.filter(v => v.status === 'offline').length;
  const alertsLastHour = alerts.filter(a => new Date(a.timestamp).getTime() > Date.now() - 3600000).length;
  const averageSpeed = vehicles.length > 0 ? Math.round(vehicles.reduce((acc, v) => acc + v.speedKph, 0) / vehicles.length) : 0;

  return (
    <div className="h-screen w-screen flex flex-col bg-background overflow-hidden">
      {/* Top Navigation Bar */}
      <header className="shrink-0 bg-card border-b border-border px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Logo & Brand */}
          <div className="flex items-center gap-6">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-primary/60 flex items-center justify-center shadow-lg glow-primary">
                <Truck className="w-5 h-5 text-white" />
              </div>
              <div>
                <h1 className="text-xl font-bold text-foreground tracking-tight">GeoFleet</h1>
                <p className="text-xs text-muted-foreground">Real-time Fleet Intelligence</p>
              </div>
            </div>

            {/* Connection Status */}
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-muted/50 border border-border/50">
              <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-success animate-pulse glow-success' : 'bg-destructive'}`} />
              <span className="text-xs font-medium text-muted-foreground">{isConnected ? 'Live Connected' : 'Disconnected'}</span>
              {isConnected && <Radio className="w-3 h-3 text-success animate-pulse" />}
            </div>
          </div>

          {/* Stats Cards */}
          <div className="flex items-center gap-4">
            <StatCard
              icon={<Activity className="w-4 h-4" />}
              label="Fleet Status"
              value={`${onlineCount}/${vehicles.length}`}
              subValue="vehicles online"
              variant="success"
            />
            <StatCard
              icon={<AlertTriangle className="w-4 h-4" />}
              label="Alerts"
              value={alertsLastHour.toString()}
              subValue="last hour"
              variant={alertsLastHour > 5 ? 'destructive' : 'warning'}
            />
            <StatCard
              icon={<Gauge className="w-4 h-4" />}
              label="Avg Speed"
              value={`${averageSpeed}`}
              subValue="km/h"
              variant="primary"
            />
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 flex min-h-0 p-4 gap-4">
        {/* Left Sidebar - Vehicle List */}
        <aside className="w-80 shrink-0 bg-card border border-border rounded-2xl flex flex-col overflow-hidden shadow-xl">
          {/* Header */}
          <div className="px-4 py-4 border-b border-border bg-muted/20">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-primary/20 flex items-center justify-center">
                  <Truck className="w-4 h-4 text-primary" />
                </div>
                <div>
                  <h2 className="font-semibold text-foreground">Vehicles</h2>
                  <p className="text-xs text-muted-foreground">{vehicles.length} total</p>
                </div>
              </div>
            </div>

            {/* Status Pills */}
            <div className="flex items-center gap-2 mb-3">
              <StatusPill color="success" count={onlineCount} label="Online" />
              <StatusPill color="warning" count={idleCount} label="Idle" />
              <StatusPill color="destructive" count={offlineCount} label="Offline" />
            </div>

            {/* Search */}
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="Search vehicles..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9 h-9 text-sm bg-background border-border focus:border-primary focus:ring-1 focus:ring-primary/30"
              />
            </div>
          </div>

          {/* Vehicle List */}
          <ScrollArea className="flex-1">
            <div className="p-2 space-y-1">
              {filteredVehicles.map((v) => (
                <VehicleItem
                  key={v.vehicleId}
                  vehicle={v}
                  isSelected={v.vehicleId === highlightedVehicleId}
                  onClick={() => handleVehicleSelect(v.vehicleId)}
                />
              ))}
            </div>
          </ScrollArea>
        </aside>

        {/* Map Area */}
        <section className="flex-1 min-w-0">
          <FleetMap
            vehicles={vehicles}
            highlightedVehicleId={highlightedVehicleId}
            onVehicleSelect={handleVehicleSelect}
          />
        </section>

        {/* Right Sidebar - Alerts */}
        <aside className="w-80 shrink-0 bg-card border border-border rounded-2xl flex flex-col overflow-hidden shadow-xl">
          {/* Header */}
          <div className="px-4 py-4 border-b border-border bg-muted/20">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-warning/20 flex items-center justify-center">
                  <Shield className="w-4 h-4 text-warning" />
                </div>
                <div>
                  <h2 className="font-semibold text-foreground">Live Alerts</h2>
                  <p className="text-xs text-muted-foreground">Real-time notifications</p>
                </div>
              </div>
              <span className="text-xs font-semibold text-primary bg-primary/10 px-3 py-1.5 rounded-full border border-primary/20">
                {alerts.length}
              </span>
            </div>
          </div>

          {/* Alert List */}
          <ScrollArea className="flex-1">
            <div className="p-2 space-y-2">
              {alerts.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <div className="w-16 h-16 rounded-full bg-muted/50 flex items-center justify-center mb-4">
                    <Shield className="w-8 h-8 text-muted-foreground" />
                  </div>
                  <p className="text-sm font-medium text-muted-foreground">No alerts</p>
                  <p className="text-xs text-muted-foreground/70 mt-1">All systems operating normally</p>
                </div>
              ) : (
                alerts.map((a) => (
                  <AlertItem key={a.id} alert={a} onClick={() => handleAlertClick(a.vehicleId)} />
                ))
              )}
            </div>
          </ScrollArea>
        </aside>
      </main>
    </div>
  );
};

function StatCard({ icon, label, value, subValue, variant }: {
  icon: React.ReactNode;
  label: string;
  value: string;
  subValue: string;
  variant: 'primary' | 'success' | 'warning' | 'destructive'
}) {
  const colors = {
    primary: 'text-primary bg-primary/10 border-primary/20',
    success: 'text-success bg-success/10 border-success/20',
    warning: 'text-warning bg-warning/10 border-warning/20',
    destructive: 'text-destructive bg-destructive/10 border-destructive/20'
  };
  const iconColors = {
    primary: 'text-primary',
    success: 'text-success',
    warning: 'text-warning',
    destructive: 'text-destructive'
  };

  return (
    <div className={`flex items-center gap-3 px-4 py-2.5 rounded-xl border ${colors[variant]}`}>
      <div className={iconColors[variant]}>{icon}</div>
      <div>
        <p className="text-xs text-muted-foreground font-medium">{label}</p>
        <div className="flex items-baseline gap-1.5">
          <span className="text-lg font-bold text-foreground">{value}</span>
          <span className="text-xs text-muted-foreground">{subValue}</span>
        </div>
      </div>
    </div>
  );
}

function StatusPill({ color, count, label }: { color: 'success' | 'warning' | 'destructive'; count: number; label: string }) {
  const colors = {
    success: 'bg-success/20 text-success border-success/30',
    warning: 'bg-warning/20 text-warning border-warning/30',
    destructive: 'bg-destructive/20 text-destructive border-destructive/30'
  };
  const dotColors = {
    success: 'bg-success',
    warning: 'bg-warning',
    destructive: 'bg-destructive'
  };

  return (
    <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${colors[color]}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${dotColors[color]}`} />
      <span>{count}</span>
      <span className="opacity-70">{label}</span>
    </div>
  );
}

function VehicleItem({ vehicle, isSelected, onClick }: { vehicle: Vehicle; isSelected: boolean; onClick: () => void }) {
  const statusConfig = {
    online: { color: 'bg-success', ring: 'ring-success/30', text: 'text-success' },
    idle: { color: 'bg-warning', ring: 'ring-warning/30', text: 'text-warning' },
    offline: { color: 'bg-destructive', ring: 'ring-destructive/30', text: 'text-destructive' }
  };
  const status = vehicle.status || 'online';
  const config = statusConfig[status];

  return (
    <button
      onClick={onClick}
      className={`w-full text-left p-3 rounded-xl transition-all duration-200 group
        ${isSelected
          ? 'bg-primary/10 border border-primary/40 shadow-lg shadow-primary/10'
          : 'border border-transparent hover:bg-muted/50 hover:border-border'
        }`}
    >
      <div className="flex items-center gap-3">
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center bg-muted/50 ring-2 ${config.ring}`}>
          <div className={`w-3 h-3 rounded-full ${config.color} ${status === 'online' ? 'animate-pulse' : ''}`} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-sm text-foreground">{vehicle.vehicleId}</span>
            <ChevronRight className={`w-4 h-4 text-muted-foreground transition-transform ${isSelected ? 'rotate-90' : 'group-hover:translate-x-0.5'}`} />
          </div>
          <div className="flex items-center gap-3 mt-1">
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <Gauge className="w-3 h-3" />
              {Math.round(vehicle.speedKph)} km/h
            </span>
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <Clock className="w-3 h-3" />
              {formatDistanceToNow(new Date(vehicle.timestamp), { addSuffix: true })}
            </span>
          </div>
        </div>
      </div>
    </button>
  );
}

function AlertItem({ alert, onClick }: { alert: Alert; onClick: () => void }) {
  const alertConfig = {
    SPEEDING: {
      bg: 'bg-destructive/5 hover:bg-destructive/10',
      border: 'border-l-destructive',
      icon: <Zap className="w-4 h-4 text-destructive" />,
      badge: 'bg-destructive/20 text-destructive'
    },
    GEOFENCE: {
      bg: 'bg-primary/5 hover:bg-primary/10',
      border: 'border-l-primary',
      icon: <MapPin className="w-4 h-4 text-primary" />,
      badge: 'bg-primary/20 text-primary'
    },
    IDLE: {
      bg: 'bg-warning/5 hover:bg-warning/10',
      border: 'border-l-warning',
      icon: <Clock className="w-4 h-4 text-warning" />,
      badge: 'bg-warning/20 text-warning'
    }
  };

  const config = alertConfig[alert.alertType];
  const detail = alert.alertType === 'SPEEDING'
    ? `${alert.details.speedKph} km/h`
    : alert.alertType === 'GEOFENCE'
      ? `${alert.details.geofence || alert.details.zone}`
      : `${alert.details.idleMinutes} min`;

  return (
    <button
      onClick={onClick}
      className={`w-full text-left p-3 rounded-xl transition-all duration-200 border-l-4 ${config.border} ${config.bg}`}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 w-8 h-8 rounded-lg bg-muted/50 flex items-center justify-center">
          {config.icon}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <span className="font-semibold text-sm text-foreground">{alert.vehicleId}</span>
            <span className="text-[10px] text-muted-foreground">
              {formatDistanceToNow(new Date(alert.timestamp), { addSuffix: true })}
            </span>
          </div>
          <div className="flex items-center gap-2 mt-1.5">
            <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${config.badge}`}>
              {alert.alertType}
            </span>
            <span className="text-xs text-muted-foreground">{detail}</span>
          </div>
        </div>
      </div>
    </button>
  );
}

export default Index;
