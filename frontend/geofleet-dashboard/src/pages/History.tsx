import { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { format, subDays, subHours } from 'date-fns';
import { Calendar, Clock, Zap, MapPin, Filter, BarChart3, AlertTriangle, Trash2, CheckSquare, Square, XCircle, SlidersHorizontal } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Slider } from '@/components/ui/slider';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

interface HistoricalAlert {
    id: number;
    vehicleId: string;
    alertType: string;
    details: string;
    timestamp: string;
    lat: number;
    lng: number;
}

interface AlertSummary {
    totalAlerts: number;
    alertsByType: Record<string, number>;
    alertsByVehicle: Record<string, number>;
    topOffenders: any[];
}

type DatePreset = '1h' | '6h' | '24h' | '7d' | '30d' | 'custom';

export default function History() {
    const [alerts, setAlerts] = useState<HistoricalAlert[]>([]);
    const [summary, setSummary] = useState<AlertSummary | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Selection state
    const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
    const [deleteLoading, setDeleteLoading] = useState(false);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState<'selected' | 'filtered' | 'all' | 'slider' | null>(null);

    // Slider state for bulk delete
    const [deleteCount, setDeleteCount] = useState<number>(100);

    // Filters
    const [datePreset, setDatePreset] = useState<DatePreset>('24h');
    const [fromDate, setFromDate] = useState<string>(format(subDays(new Date(), 1), "yyyy-MM-dd'T'HH:mm"));
    const [toDate, setToDate] = useState<string>(format(new Date(), "yyyy-MM-dd'T'HH:mm"));
    const [alertType, setAlertType] = useState<string>('all');
    const [vehicleId, setVehicleId] = useState<string>('');

    // Calculate date range based on preset
    useEffect(() => {
        const now = new Date();
        let from: Date;

        switch (datePreset) {
            case '1h':
                from = subHours(now, 1);
                break;
            case '6h':
                from = subHours(now, 6);
                break;
            case '24h':
                from = subDays(now, 1);
                break;
            case '7d':
                from = subDays(now, 7);
                break;
            case '30d':
                from = subDays(now, 30);
                break;
            default:
                return;
        }

        setFromDate(format(from, "yyyy-MM-dd'T'HH:mm"));
        setToDate(format(now, "yyyy-MM-dd'T'HH:mm"));
    }, [datePreset]);

    // Fetch historical data
    const fetchHistory = async () => {
        setLoading(true);
        setError(null);
        setSelectedIds(new Set()); // Clear selection on refresh

        try {
            const fromDateTime = fromDate.includes(':') ? fromDate + ':00' : fromDate;
            const toDateTime = toDate.includes(':') ? toDate + ':00' : toDate;

            const params = new URLSearchParams({
                from: fromDateTime,
                to: toDateTime,
                limit: '200',
            });

            if (alertType !== 'all') {
                params.append('type', alertType);
            }
            if (vehicleId) {
                params.append('vehicleId', vehicleId);
            }

            const [alertsRes, summaryRes] = await Promise.all([
                fetch(`${API_BASE_URL}/vehicles/alerts/history?${params}`),
                fetch(`${API_BASE_URL}/vehicles/alerts/summary?from=${fromDateTime}&to=${toDateTime}`),
            ]);

            if (!alertsRes.ok || !summaryRes.ok) {
                throw new Error('Failed to fetch historical data');
            }

            const alertsData = await alertsRes.json();
            const summaryData = await summaryRes.json();

            setAlerts(alertsData.alerts || []);
            setSummary(summaryData);
        } catch (err) {
            console.error('Error fetching history:', err);
            setError(err instanceof Error ? err.message : 'Failed to load data');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchHistory();
    }, [fromDate, toDate, alertType, vehicleId]);

    // Selection handlers
    const toggleSelect = (id: number) => {
        setSelectedIds(prev => {
            const newSet = new Set(prev);
            if (newSet.has(id)) {
                newSet.delete(id);
            } else {
                newSet.add(id);
            }
            return newSet;
        });
    };

    const selectAll = () => {
        if (selectedIds.size === alerts.length) {
            setSelectedIds(new Set());
        } else {
            setSelectedIds(new Set(alerts.map(a => a.id)));
        }
    };

    // Delete handlers
    const deleteSelected = async () => {
        if (selectedIds.size === 0) return;

        setDeleteLoading(true);
        try {
            const ids = Array.from(selectedIds);
            const res = await fetch(`${API_BASE_URL}/vehicles/alerts/batch?ids=${ids.join(',')}`, {
                method: 'DELETE',
            });

            if (!res.ok) throw new Error('Failed to delete alerts');

            const data = await res.json();
            console.log('Deleted:', data);

            setShowDeleteConfirm(null);
            fetchHistory(); // Refresh
        } catch (err) {
            console.error('Delete error:', err);
            setError('Failed to delete alerts');
        } finally {
            setDeleteLoading(false);
        }
    };

    const deleteFiltered = async () => {
        setDeleteLoading(true);
        try {
            const fromDateTime = fromDate.includes(':') ? fromDate + ':00' : fromDate;
            const toDateTime = toDate.includes(':') ? toDate + ':00' : toDate;

            const params = new URLSearchParams({
                from: fromDateTime,
                to: toDateTime,
            });

            if (alertType !== 'all') {
                params.append('type', alertType);
            }
            if (vehicleId) {
                params.append('vehicleId', vehicleId);
            }

            const res = await fetch(`${API_BASE_URL}/vehicles/alerts/filtered?${params}`, {
                method: 'DELETE',
            });

            if (!res.ok) throw new Error('Failed to delete alerts');

            const data = await res.json();
            console.log('Deleted:', data);

            setShowDeleteConfirm(null);
            fetchHistory();
        } catch (err) {
            console.error('Delete error:', err);
            setError('Failed to delete alerts');
        } finally {
            setDeleteLoading(false);
        }
    };

    const deleteAllAlerts = async () => {
        setDeleteLoading(true);
        try {
            const res = await fetch(`${API_BASE_URL}/vehicles/alerts/all?confirm=true`, {
                method: 'DELETE',
            });

            if (!res.ok) throw new Error('Failed to delete all alerts');

            const data = await res.json();
            console.log('Deleted all:', data);

            setShowDeleteConfirm(null);
            fetchHistory();
        } catch (err) {
            console.error('Delete error:', err);
            setError('Failed to delete alerts');
        } finally {
            setDeleteLoading(false);
        }
    };

    const deleteSingle = async (id: number) => {
        try {
            const res = await fetch(`${API_BASE_URL}/vehicles/alerts/${id}`, {
                method: 'DELETE',
            });

            if (!res.ok) throw new Error('Failed to delete alert');

            // Remove from local state immediately
            setAlerts(prev => prev.filter(a => a.id !== id));
            setSelectedIds(prev => {
                const newSet = new Set(prev);
                newSet.delete(id);
                return newSet;
            });
        } catch (err) {
            console.error('Delete error:', err);
            setError('Failed to delete alert');
        }
    };

    // Delete by slider count (deletes the first N alerts from current view)
    const deleteBySliderCount = async () => {
        if (deleteCount === 0 || alerts.length === 0) return;

        setDeleteLoading(true);
        try {
            const idsToDelete = alerts.slice(0, deleteCount).map(a => a.id);
            const res = await fetch(`${API_BASE_URL}/vehicles/alerts/batch?ids=${idsToDelete.join(',')}`, {
                method: 'DELETE',
            });

            if (!res.ok) throw new Error('Failed to delete alerts');

            const data = await res.json();
            console.log('Deleted:', data);

            setShowDeleteConfirm(null);
            fetchHistory();
        } catch (err) {
            console.error('Delete error:', err);
            setError('Failed to delete alerts');
        } finally {
            setDeleteLoading(false);
        }
    };

    const parseDetails = (details: string): Record<string, any> => {
        try {
            return typeof details === 'string' ? JSON.parse(details) : details;
        } catch {
            return {};
        }
    };

    const getAlertIcon = (type: string) => {
        switch (type) {
            case 'SPEEDING':
                return <Zap className="w-4 h-4 text-destructive" />;
            case 'GEOFENCE_ENTER':
            case 'GEOFENCE_EXIT':
            case 'GEOFENCE':
                return <MapPin className="w-4 h-4 text-primary" />;
            case 'IDLE':
                return <Clock className="w-4 h-4 text-warning" />;
            default:
                return <AlertTriangle className="w-4 h-4 text-muted-foreground" />;
        }
    };

    const getAlertColor = (type: string) => {
        switch (type) {
            case 'SPEEDING':
                return 'border-l-destructive bg-destructive/5';
            case 'GEOFENCE_ENTER':
            case 'GEOFENCE_EXIT':
            case 'GEOFENCE':
                return 'border-l-primary bg-primary/5';
            case 'IDLE':
                return 'border-l-warning bg-warning/5';
            default:
                return 'border-l-muted bg-muted/5';
        }
    };

    return (
        <div className="h-screen w-screen flex flex-col bg-background overflow-hidden">
            {/* Delete Confirmation Modal */}
            {showDeleteConfirm && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-card border border-border rounded-2xl p-6 max-w-md w-full mx-4 shadow-2xl">
                        <div className="flex items-center gap-3 mb-4">
                            <div className="w-10 h-10 rounded-full bg-destructive/10 flex items-center justify-center">
                                <Trash2 className="w-5 h-5 text-destructive" />
                            </div>
                            <h3 className="text-lg font-semibold text-foreground">Confirm Delete</h3>
                        </div>

                        <p className="text-sm text-muted-foreground mb-6">
                            {showDeleteConfirm === 'selected' && `Are you sure you want to delete ${selectedIds.size} selected alert(s)?`}
                            {showDeleteConfirm === 'filtered' && `Are you sure you want to delete all ${alerts.length} alerts matching current filters?`}
                            {showDeleteConfirm === 'all' && `⚠️ Are you sure you want to delete ALL alerts from the database? This cannot be undone!`}
                            {showDeleteConfirm === 'slider' && `Are you sure you want to delete ${deleteCount} alert(s)?`}
                        </p>

                        <div className="flex gap-3 justify-end">
                            <button
                                onClick={() => setShowDeleteConfirm(null)}
                                className="px-4 py-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
                                disabled={deleteLoading}
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => {
                                    if (showDeleteConfirm === 'selected') deleteSelected();
                                    else if (showDeleteConfirm === 'filtered') deleteFiltered();
                                    else if (showDeleteConfirm === 'all') deleteAllAlerts();
                                    else if (showDeleteConfirm === 'slider') deleteBySliderCount();
                                }}
                                disabled={deleteLoading}
                                className="px-4 py-2 text-sm font-medium bg-destructive text-destructive-foreground rounded-lg hover:bg-destructive/90 transition-colors disabled:opacity-50"
                            >
                                {deleteLoading ? 'Deleting...' : 'Delete'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Header */}
            <header className="shrink-0 bg-card border-b border-border px-6 py-4">
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-primary/60 flex items-center justify-center shadow-lg">
                            <BarChart3 className="w-5 h-5 text-white" />
                        </div>
                        <div>
                            <h1 className="text-xl font-bold text-foreground tracking-tight">Historical Data</h1>
                            <p className="text-xs text-muted-foreground">View and analyze past alerts</p>
                        </div>
                    </div>

                    <Link
                        to="/"
                        className="px-4 py-2 text-sm font-medium text-primary hover:text-primary/80 transition-colors"
                    >
                        ← Back to Dashboard
                    </Link>
                </div>
            </header>

            {/* Main Content */}
            <main className="flex-1 flex min-h-0 p-4 gap-4">
                {/* Filters Sidebar */}
                <aside className="w-72 shrink-0 bg-card border border-border rounded-2xl flex flex-col overflow-hidden shadow-xl">
                    <ScrollArea className="flex-1">
                        <div className="px-4 py-4 border-b border-border bg-muted/20">
                            <div className="flex items-center gap-2 mb-4">
                                <Filter className="w-4 h-4 text-primary" />
                                <h2 className="font-semibold text-foreground">Filters</h2>
                            </div>

                            <div className="space-y-3">
                                <label className="text-xs font-medium text-muted-foreground">Time Range</label>
                                <Select value={datePreset} onValueChange={(v) => setDatePreset(v as DatePreset)}>
                                    <SelectTrigger className="h-9 text-sm bg-background border-border">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="1h">Last 1 Hour</SelectItem>
                                        <SelectItem value="6h">Last 6 Hours</SelectItem>
                                        <SelectItem value="24h">Last 24 Hours</SelectItem>
                                        <SelectItem value="7d">Last 7 Days</SelectItem>
                                        <SelectItem value="30d">Last 30 Days</SelectItem>
                                        <SelectItem value="custom">Custom Range</SelectItem>
                                    </SelectContent>
                                </Select>

                                {datePreset === 'custom' && (
                                    <>
                                        <div>
                                            <label className="text-xs font-medium text-muted-foreground">From</label>
                                            <Input
                                                type="datetime-local"
                                                value={fromDate}
                                                onChange={(e) => setFromDate(e.target.value)}
                                                className="h-9 text-sm bg-background border-border mt-1"
                                            />
                                        </div>
                                        <div>
                                            <label className="text-xs font-medium text-muted-foreground">To</label>
                                            <Input
                                                type="datetime-local"
                                                value={toDate}
                                                onChange={(e) => setToDate(e.target.value)}
                                                className="h-9 text-sm bg-background border-border mt-1"
                                            />
                                        </div>
                                    </>
                                )}

                                <div>
                                    <label className="text-xs font-medium text-muted-foreground">Alert Type</label>
                                    <Select value={alertType} onValueChange={setAlertType}>
                                        <SelectTrigger className="h-9 text-sm bg-background border-border mt-1">
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="all">All Types</SelectItem>
                                            <SelectItem value="SPEEDING">Speeding</SelectItem>
                                            <SelectItem value="GEOFENCE_ENTER">Geofence Enter</SelectItem>
                                            <SelectItem value="GEOFENCE_EXIT">Geofence Exit</SelectItem>
                                            <SelectItem value="IDLE">Idle</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </div>

                                <div>
                                    <label className="text-xs font-medium text-muted-foreground">Vehicle ID</label>
                                    <Input
                                        placeholder="e.g., TRK-01"
                                        value={vehicleId}
                                        onChange={(e) => setVehicleId(e.target.value)}
                                        className="h-9 text-sm bg-background border-border mt-1"
                                    />
                                </div>

                                <button
                                    onClick={fetchHistory}
                                    disabled={loading}
                                    className="w-full h-9 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors disabled:opacity-50"
                                >
                                    {loading ? 'Loading...' : 'Apply Filters'}
                                </button>
                            </div>
                        </div>

                        {/* Summary Stats */}
                        {summary && (
                            <div className="p-4 space-y-4">
                                <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Summary</h3>

                                <div className="grid grid-cols-2 gap-3">
                                    <div className="p-3 rounded-xl bg-muted/30 text-center">
                                        <div className="text-2xl font-bold text-foreground">{summary.totalAlerts}</div>
                                        <div className="text-xs text-muted-foreground">Total Alerts</div>
                                    </div>
                                    <div className="p-3 rounded-xl bg-destructive/10 text-center">
                                        <div className="text-2xl font-bold text-destructive">{summary.alertsByType?.SPEEDING || 0}</div>
                                        <div className="text-xs text-muted-foreground">Speeding</div>
                                    </div>
                                    <div className="p-3 rounded-xl bg-primary/10 text-center">
                                        <div className="text-2xl font-bold text-primary">
                                            {(summary.alertsByType?.GEOFENCE_ENTER || 0) + (summary.alertsByType?.GEOFENCE_EXIT || 0)}
                                        </div>
                                        <div className="text-xs text-muted-foreground">Geofence</div>
                                    </div>
                                    <div className="p-3 rounded-xl bg-warning/10 text-center">
                                        <div className="text-2xl font-bold text-warning">{summary.alertsByType?.IDLE || 0}</div>
                                        <div className="text-xs text-muted-foreground">Idle</div>
                                    </div>
                                </div>

                                {summary.topOffenders && summary.topOffenders.length > 0 && (
                                    <div>
                                        <h4 className="text-xs font-semibold text-muted-foreground mb-2">Top Vehicles</h4>
                                        <div className="space-y-2">
                                            {summary.topOffenders.slice(0, 5).map((item: any, i: number) => {
                                                const vehicle = item.key || item[0] || 'Unknown';
                                                const count = item.value || item[1] || 0;
                                                return (
                                                    <div key={vehicle} className="flex items-center justify-between text-sm">
                                                        <div className="flex items-center gap-2">
                                                            <span className="w-5 h-5 rounded-full bg-muted flex items-center justify-center text-xs font-medium">
                                                                {i + 1}
                                                            </span>
                                                            <span className="text-foreground">{vehicle}</span>
                                                        </div>
                                                        <span className="text-muted-foreground">{count} alerts</span>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}

                        {/* Delete Actions */}
                        <div className="mt-auto p-4 border-t border-border space-y-3">
                            <div className="flex items-center gap-2 mb-3">
                                <SlidersHorizontal className="w-4 h-4 text-destructive" />
                                <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Delete Alerts</h3>
                            </div>

                            {/* Slider for bulk delete */}
                            <div className="space-y-2 p-3 rounded-xl bg-destructive/5 border border-destructive/20">
                                <div className="flex items-center justify-between">
                                    <label className="text-xs font-medium text-muted-foreground">Delete Count</label>
                                    <span className="text-sm font-bold text-destructive">{deleteCount}</span>
                                </div>
                                <Slider
                                    value={[deleteCount]}
                                    onValueChange={(value) => setDeleteCount(value[0])}
                                    max={Math.max(alerts.length, 200)}
                                    min={0}
                                    step={10}
                                    className="w-full"
                                />
                                <div className="flex justify-between text-[10px] text-muted-foreground">
                                    <span>0</span>
                                    <span>{Math.max(alerts.length, 200)}</span>
                                </div>
                                <button
                                    onClick={() => setShowDeleteConfirm('slider')}
                                    disabled={deleteCount === 0 || alerts.length === 0}
                                    className="w-full h-9 bg-destructive text-destructive-foreground rounded-lg text-sm font-medium hover:bg-destructive/90 transition-colors disabled:opacity-30 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                                >
                                    <Trash2 className="w-4 h-4" />
                                    Delete {deleteCount} Alerts
                                </button>
                            </div>

                            <div className="space-y-2">
                                <button
                                    onClick={() => setShowDeleteConfirm('selected')}
                                    disabled={selectedIds.size === 0}
                                    className="w-full h-8 bg-muted/50 text-muted-foreground rounded-lg text-xs font-medium hover:bg-muted transition-colors disabled:opacity-30 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                                >
                                    <Trash2 className="w-3 h-3" />
                                    Delete Selected ({selectedIds.size})
                                </button>

                                <button
                                    onClick={() => setShowDeleteConfirm('filtered')}
                                    disabled={alerts.length === 0}
                                    className="w-full h-8 bg-warning/10 text-warning rounded-lg text-xs font-medium hover:bg-warning/20 transition-colors disabled:opacity-30 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                                >
                                    <Trash2 className="w-3 h-3" />
                                    Delete All Filtered ({alerts.length})
                                </button>

                                <button
                                    onClick={() => setShowDeleteConfirm('all')}
                                    className="w-full h-8 border border-destructive/50 text-destructive rounded-lg text-xs font-medium hover:bg-destructive/10 transition-colors flex items-center justify-center gap-2"
                                >
                                    <Trash2 className="w-3 h-3" />
                                    Delete ALL Alerts
                                </button>
                            </div>
                        </div>
                    </ScrollArea>
                </aside>

                {/* Alerts List */}
                <section className="flex-1 bg-card border border-border rounded-2xl flex flex-col overflow-hidden shadow-xl">
                    <div className="px-4 py-4 border-b border-border bg-muted/20 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <button
                                onClick={selectAll}
                                className="p-1 hover:bg-muted rounded transition-colors"
                                title={selectedIds.size === alerts.length ? "Deselect all" : "Select all"}
                            >
                                {selectedIds.size === alerts.length && alerts.length > 0 ? (
                                    <CheckSquare className="w-5 h-5 text-primary" />
                                ) : (
                                    <Square className="w-5 h-5 text-muted-foreground" />
                                )}
                            </button>
                            <Calendar className="w-4 h-4 text-primary" />
                            <h2 className="font-semibold text-foreground">Alert History</h2>
                            <span className="text-xs text-muted-foreground bg-muted px-2 py-1 rounded-full">
                                {alerts.length} results
                            </span>
                            {selectedIds.size > 0 && (
                                <span className="text-xs text-primary bg-primary/10 px-2 py-1 rounded-full">
                                    {selectedIds.size} selected
                                </span>
                            )}
                        </div>
                        <div className="text-xs text-muted-foreground">
                            {format(new Date(fromDate), 'MMM d, HH:mm')} - {format(new Date(toDate), 'MMM d, HH:mm')}
                        </div>
                    </div>

                    <ScrollArea className="flex-1">
                        <div className="p-4 space-y-2">
                            {loading ? (
                                <div className="flex items-center justify-center py-12">
                                    <div className="w-8 h-8 border-2 border-primary/30 border-t-primary rounded-full animate-spin" />
                                </div>
                            ) : error ? (
                                <div className="flex flex-col items-center justify-center py-12 text-center">
                                    <AlertTriangle className="w-12 h-12 text-destructive mb-4" />
                                    <p className="text-sm text-destructive">{error}</p>
                                    <button
                                        onClick={fetchHistory}
                                        className="mt-4 px-4 py-2 text-sm bg-primary text-primary-foreground rounded-lg"
                                    >
                                        Retry
                                    </button>
                                </div>
                            ) : alerts.length === 0 ? (
                                <div className="flex flex-col items-center justify-center py-12 text-center">
                                    <Calendar className="w-12 h-12 text-muted-foreground mb-4" />
                                    <p className="text-sm text-muted-foreground">No alerts found</p>
                                    <p className="text-xs text-muted-foreground/70 mt-1">Try adjusting your filters</p>
                                </div>
                            ) : (
                                alerts.map((alert) => {
                                    const details = parseDetails(alert.details);
                                    const isSelected = selectedIds.has(alert.id);
                                    return (
                                        <div
                                            key={alert.id}
                                            className={`p-4 rounded-xl border-l-4 transition-all hover:shadow-md ${getAlertColor(alert.alertType)} ${isSelected ? 'ring-2 ring-primary' : ''}`}
                                        >
                                            <div className="flex items-start gap-3">
                                                <button
                                                    onClick={() => toggleSelect(alert.id)}
                                                    className="mt-0.5 p-1 hover:bg-muted/50 rounded transition-colors"
                                                >
                                                    {isSelected ? (
                                                        <CheckSquare className="w-5 h-5 text-primary" />
                                                    ) : (
                                                        <Square className="w-5 h-5 text-muted-foreground" />
                                                    )}
                                                </button>
                                                <div className="mt-0.5 w-8 h-8 rounded-lg bg-muted/50 flex items-center justify-center">
                                                    {getAlertIcon(alert.alertType)}
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <div className="flex items-center justify-between gap-2">
                                                        <div className="flex items-center gap-2">
                                                            <span className="font-semibold text-sm text-foreground">{alert.vehicleId}</span>
                                                            <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-muted text-muted-foreground">
                                                                {alert.alertType.replace('_', ' ')}
                                                            </span>
                                                        </div>
                                                        <div className="flex items-center gap-2">
                                                            <span className="text-xs text-muted-foreground">
                                                                {format(new Date(alert.timestamp), 'MMM d, HH:mm:ss')}
                                                            </span>
                                                            <button
                                                                onClick={() => deleteSingle(alert.id)}
                                                                className="p-1 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded transition-colors"
                                                                title="Delete this alert"
                                                            >
                                                                <XCircle className="w-4 h-4" />
                                                            </button>
                                                        </div>
                                                    </div>
                                                    <div className="mt-2 text-xs text-muted-foreground">
                                                        {alert.alertType === 'SPEEDING' && details.speedKph && (
                                                            <span>Speed: {details.speedKph} km/h (threshold: {details.threshold} km/h)</span>
                                                        )}
                                                        {(alert.alertType === 'GEOFENCE_ENTER' || alert.alertType === 'GEOFENCE_EXIT') && (
                                                            <span>Zone: {details.geofence || details.zone || 'Unknown'}</span>
                                                        )}
                                                        {alert.alertType === 'IDLE' && details.idleMinutes && (
                                                            <span>Idle for {details.idleMinutes} minutes</span>
                                                        )}
                                                    </div>
                                                    <div className="mt-1 text-[10px] text-muted-foreground/70">
                                                        📍 {alert.lat?.toFixed(6)}, {alert.lng?.toFixed(6)}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </ScrollArea>
                </section>
            </main>
        </div>
    );
}
