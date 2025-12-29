import { useState, useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Truck, AlertTriangle, Gauge, Clock, Zap, MapPin, Search, ChevronRight, Activity, Radio, Shield, Filter, X, History } from 'lucide-react';
import { FleetMap } from '@/components/FleetMap';
import { useVehicleStream, useAlertStream } from '@/hooks/useFleetStream';
import { Vehicle, Alert } from '@/types/fleet';
import { formatDistanceToNow } from 'date-fns';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const Index = () => {
  const { vehicles, isConnected: vehiclesConnected } = useVehicleStream();
  const { alerts, isConnected: alertsConnected } = useAlertStream();
  const [highlightedVehicleId, setHighlightedVehicleId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [regionFilter, setRegionFilter] = useState<string>('all');
  const [alertTypeFilter, setAlertTypeFilter] = useState<string>('all');

  const handleAlertClick = useCallback((vehicleId: string) => {
    setHighlightedVehicleId(vehicleId);
  }, []);

  const handleVehicleSelect = useCallback((vehicleId: string) => {
    setHighlightedVehicleId(prev => prev === vehicleId ? null : vehicleId);
  }, []);

  // Get unique regions from vehicles
  const regions = useMemo(() => {
    const uniqueRegions = new Set(vehicles.map(v => v.region).filter(Boolean));
    return Array.from(uniqueRegions).sort();
  }, [vehicles]);

  const isConnected = vehiclesConnected && alertsConnected;

  // Filter vehicles by search query and region
  const filteredVehicles = useMemo(() => {
    return vehicles.filter(v => {
      const matchesSearch = v.vehicleId.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesRegion = regionFilter === 'all' || v.region === regionFilter;
      return matchesSearch && matchesRegion;
    });
  }, [vehicles, searchQuery, regionFilter]);

  // Filter alerts by type
  const filteredAlerts = useMemo(() => {
    if (alertTypeFilter === 'all') return alerts;
    return alerts.filter(a => a.alertType === alertTypeFilter);
  }, [alerts, alertTypeFilter]);

  const onlineCount = vehicles.filter(v => v.status === 'online').length;
  const idleCount = vehicles.filter(v => v.status === 'idle').length;
  const offlineCount = vehicles.filter(v => v.status === 'offline').length;
  const alertsLastHour = alerts.filter(a => new Date(a.timestamp).getTime() > Date.now() - 3600000).length;
  const averageSpeed = vehicles.length > 0 ? Math.round(vehicles.reduce((acc, v) => acc + v.speedKph, 0) / vehicles.length) : 0;

  // Alert counts by type
  const alertCounts = useMemo(() => ({
    SPEEDING: alerts.filter(a => a.alertType === 'SPEEDING').length,
    GEOFENCE: alerts.filter(a => a.alertType === 'GEOFENCE').length,
    IDLE: alerts.filter(a => a.alertType === 'IDLE').length,
  }), [alerts]);

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

            {/* History Link */}
            <Link
              to="/history"
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl border border-border bg-muted/30 hover:bg-muted/50 transition-colors"
            >
              <History className="w-4 h-4 text-primary" />
              <span className="text-sm font-medium text-foreground">History</span>
            </Link>
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
                  <p className="text-xs text-muted-foreground">{filteredVehicles.length} of {vehicles.length}</p>
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
            <div className="relative mb-3">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="Search vehicles..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9 h-9 text-sm bg-background border-border focus:border-primary focus:ring-1 focus:ring-primary/30"
              />
            </div>

            {/* Region Filter */}
            <div className="flex items-center gap-2">
              <Filter className="w-4 h-4 text-muted-foreground shrink-0" />
              <Select value={regionFilter} onValueChange={setRegionFilter}>
                <SelectTrigger className="h-8 text-xs bg-background border-border flex-1">
                  <SelectValue placeholder="All Regions" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Regions</SelectItem>
                  {regions.map(region => (
                    <SelectItem key={region} value={region || 'unknown'}>{region || 'Unknown'}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {regionFilter !== 'all' && (
                <button
                  onClick={() => setRegionFilter('all')}
                  className="p-1.5 rounded-md hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
          </div>

          {/* Vehicle List */}
          <ScrollArea className="flex-1">
            <div className="p-2 space-y-1">
              {filteredVehicles.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <div className="w-12 h-12 rounded-full bg-muted/50 flex items-center justify-center mb-3">
                    <Truck className="w-6 h-6 text-muted-foreground" />
                  </div>
                  <p className="text-sm text-muted-foreground">No vehicles found</p>
                  <p className="text-xs text-muted-foreground/70 mt-1">Try adjusting your filters</p>
                </div>
              ) : (
                filteredVehicles.map((v) => (
                  <VehicleItem
                    key={v.vehicleId}
                    vehicle={v}
                    isSelected={v.vehicleId === highlightedVehicleId}
                    onClick={() => handleVehicleSelect(v.vehicleId)}
                  />
                ))
              )}
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
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-warning/20 flex items-center justify-center">
                  <Shield className="w-4 h-4 text-warning" />
                </div>
                <div>
                  <h2 className="font-semibold text-foreground">Live Alerts</h2>
                  <p className="text-xs text-muted-foreground">{filteredAlerts.length} of {alerts.length}</p>
                </div>
              </div>
              <span className="text-xs font-semibold text-primary bg-primary/10 px-3 py-1.5 rounded-full border border-primary/20">
                {filteredAlerts.length}
              </span>
            </div>

            {/* Alert Type Filter */}
            <div className="flex items-center gap-2 mb-3">
              <Filter className="w-4 h-4 text-muted-foreground shrink-0" />
              <Select value={alertTypeFilter} onValueChange={setAlertTypeFilter}>
                <SelectTrigger className="h-8 text-xs bg-background border-border flex-1">
                  <SelectValue placeholder="All Types" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All Types ({alerts.length})</SelectItem>
                  <SelectItem value="SPEEDING">Speeding ({alertCounts.SPEEDING})</SelectItem>
                  <SelectItem value="GEOFENCE">Geofence ({alertCounts.GEOFENCE})</SelectItem>
                  <SelectItem value="IDLE">Idle ({alertCounts.IDLE})</SelectItem>
                </SelectContent>
              </Select>
              {alertTypeFilter !== 'all' && (
                <button
                  onClick={() => setAlertTypeFilter('all')}
                  className="p-1.5 rounded-md hover:bg-muted text-muted-foreground hover:text-foreground transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>

            {/* Alert Type Pills */}
            <div className="flex items-center gap-2">
              <AlertTypePill
                type="SPEEDING"
                count={alertCounts.SPEEDING}
                isActive={alertTypeFilter === 'SPEEDING'}
                onClick={() => setAlertTypeFilter(alertTypeFilter === 'SPEEDING' ? 'all' : 'SPEEDING')}
              />
              <AlertTypePill
                type="GEOFENCE"
                count={alertCounts.GEOFENCE}
                isActive={alertTypeFilter === 'GEOFENCE'}
                onClick={() => setAlertTypeFilter(alertTypeFilter === 'GEOFENCE' ? 'all' : 'GEOFENCE')}
              />
              <AlertTypePill
                type="IDLE"
                count={alertCounts.IDLE}
                isActive={alertTypeFilter === 'IDLE'}
                onClick={() => setAlertTypeFilter(alertTypeFilter === 'IDLE' ? 'all' : 'IDLE')}
              />
            </div>
          </div>

          {/* Alert List */}
          <ScrollArea className="flex-1">
            <div className="p-2 space-y-2">
              {filteredAlerts.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <div className="w-16 h-16 rounded-full bg-muted/50 flex items-center justify-center mb-4">
                    <Shield className="w-8 h-8 text-muted-foreground" />
                  </div>
                  <p className="text-sm font-medium text-muted-foreground">No alerts</p>
                  <p className="text-xs text-muted-foreground/70 mt-1">
                    {alertTypeFilter !== 'all' ? 'Try changing the filter' : 'All systems operating normally'}
                  </p>
                </div>
              ) : (
                filteredAlerts.map((a) => (
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

function AlertTypePill({ type, count, isActive, onClick }: {
  type: 'SPEEDING' | 'GEOFENCE' | 'IDLE';
  count: number;
  isActive: boolean;
  onClick: () => void;
}) {
  const config = {
    SPEEDING: { icon: <Zap className="w-3 h-3" />, color: 'destructive' as const },
    GEOFENCE: { icon: <MapPin className="w-3 h-3" />, color: 'primary' as const },
    IDLE: { icon: <Clock className="w-3 h-3" />, color: 'warning' as const },
  };

  const { icon, color } = config[type];
  const colors = {
    primary: isActive ? 'bg-primary/30 text-primary border-primary/50' : 'bg-primary/10 text-primary/70 border-primary/20 hover:bg-primary/20',
    destructive: isActive ? 'bg-destructive/30 text-destructive border-destructive/50' : 'bg-destructive/10 text-destructive/70 border-destructive/20 hover:bg-destructive/20',
    warning: isActive ? 'bg-warning/30 text-warning border-warning/50' : 'bg-warning/10 text-warning/70 border-warning/20 hover:bg-warning/20',
  };

  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-1 px-2 py-1 rounded-full text-[10px] font-semibold border transition-all ${colors[color]}`}
    >
      {icon}
      <span>{count}</span>
    </button>
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
          {vehicle.region && (
            <div className="flex items-center gap-1 mt-1">
              <MapPin className="w-3 h-3 text-primary/60" />
              <span className="text-xs text-primary/80">{vehicle.region}</span>
            </div>
          )}
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
