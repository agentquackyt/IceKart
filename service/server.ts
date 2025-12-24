import { serve } from "bun";

interface LapData {
    lapNumber: number;
    lapTime: number;
    splits: number[];
}

interface Racer {
    id: string;
    name: string;
    avatar: string;
    laps: number;
    bestLap: number | null; // in milliseconds
    lastLapTimestamp: number;
    totalTime: number; // cumulative time from race start
    disqualified: boolean;
    checkpoints: number; // checkpoint count within current lap
    gap: number; // time difference from the leader at this position
    finished: boolean;
    history: LapData[]; // History of completed laps
    currentLapSplits: number[]; // Splits for the current ongoing lap
}

const CHECKPOINTS_PER_LAP = Bun.env.CHECKPOINTS_PER_LAP ? parseInt(Bun.env.CHECKPOINTS_PER_LAP) : 10; // Number of checkpoints before a lap is complete

interface GameState {
    status: 'idle' | 'racing' | 'stopped' | 'finishing';
    racers: Racer[];
    startTime: number | null;
    endTime: number | null;
    totalLaps: number;
}

// Track the best time for each position (lap-checkpoint) to calculate gaps
const courseRecords = new Map<string, number>();

const INITIAL_RACERS: Racer[] = [];

let gameState: GameState = {
    status: 'idle',
    racers: JSON.parse(JSON.stringify(INITIAL_RACERS)),
    startTime: null,
    endTime: null,
    totalLaps: Bun.env.TOTAL_LAPS ? parseInt(Bun.env.TOTAL_LAPS) : 3,
};

const server = serve({
    port: 3000,
    async fetch(req, server) {
        const url = new URL(req.url);

        // WebSocket upgrade
        if (url.pathname === '/ws') {
            if (server.upgrade(req)) return;
            return new Response("Upgrade failed", { status: 500 });
        }

        // Static files
        if (url.pathname.startsWith('/leaderboard/') || url.pathname.startsWith('/manager/') || url.pathname.startsWith('/podium/') || url.pathname.startsWith('/splash/') || url.pathname.startsWith('/racer/')) {
            let filePath = '.' + url.pathname;
            if (filePath.endsWith('/')) {
                if (filePath.includes('/leaderboard/')) {
                    filePath += 'leaderboard.html';
                } else if (filePath.includes('/manager/')) {
                    filePath += 'index.html';
                } else if (filePath.includes('/podium/')) {
                    filePath += 'podium.html';
                } else if (filePath.includes('/splash/')) {
                    filePath += 'splash.html';
                } else if (filePath.includes('/racer/')) {
                    filePath += 'index.html';
                }
            }
            
            const file = Bun.file(filePath);
            if (await file.exists()) {
                return new Response(file);
            }
        }

        // Redirect root
        if (url.pathname === '/') {
            return Response.redirect('/leaderboard/');
        }

        // Get race results (sorted)
        if (url.pathname === '/api/results' && req.method === 'GET') {
            const sortedRacers = [...gameState.racers]
                .filter(r => !r.disqualified)
                .sort((a, b) => {
                    if (b.laps !== a.laps) return b.laps - a.laps;
                    if (b.checkpoints !== a.checkpoints) return b.checkpoints - a.checkpoints;
                    const timeA = a.totalTime || Infinity;
                    const timeB = b.totalTime || Infinity;
                    return timeA - timeB;
                });
            
            // Add disqualified at the end
            const disqualified = gameState.racers.filter(r => r.disqualified);
            
            return new Response(JSON.stringify({ racers: [...sortedRacers, ...disqualified] }), {
                headers: { "Content-Type": "application/json" }
            });
        }

        // Register racer
        if (url.pathname === '/api/register' && req.method === 'POST') {
            try {
                const body = await req.json();
                const name = body.name;
                
                if (!name) {
                    return new Response("Name is required", { status: 400 });
                }

                if (gameState.racers.some(r => r.name === name)) {
                    return new Response("Racer with this name already exists", { status: 409 });
                }
                
                const newRacer: Racer = {
                    id: `r${Date.now()}`,
                    name: name,
                    avatar: '',
                    laps: 0,
                    bestLap: null,
                    lastLapTimestamp: 0,
                    totalTime: 0,
                    disqualified: false,
                    checkpoints: 0,
                    gap: 0,
                    finished: false,
                    history: [],
                    currentLapSplits: []
                };
                
                gameState.racers.push(newRacer);
                console.log(`\x1b[32m[RACER] Added ${newRacer.name}\x1b[0m`);
                
                // Broadcast update
                server.publish("all", JSON.stringify({
                    type: 'update',
                    racers: gameState.racers
                }));

                return new Response(JSON.stringify(newRacer), { 
                    headers: { "Content-Type": "application/json" } 
                });
            } catch (e) {
                return new Response("Invalid request", { status: 400 });
            }
        }

        return new Response("Not Found", { status: 404 });
    },
    websocket: {
        open(ws) {
            ws.subscribe("all");
            ws.send(JSON.stringify({
                type: 'init',
                status: gameState.status,
                racers: gameState.racers,
                startTime: gameState.startTime,
                endTime: gameState.endTime,
                totalLaps: gameState.totalLaps
            }));
        },
        message(ws, message) {
            const data = JSON.parse(typeof message === 'string' ? message : new TextDecoder().decode(message));

            if (data.type === 'action') {
                handleAction(data.payload);
            } else if (data.type === 'lap') {
                handleLap(data.racerId);
            } else if (data.type === 'checkpoint') {
                handleCheckpoint(data.racerId);
            } else if (data.type === 'disqualify') {
                handleDisqualify(data.racerId);
            } else if (data.type === 'remove') {
                handleRemove(data.name);
            } else if (data.type === 'register') {
                handleRegister(data.name);
            }
        },
    },
});

function handleAction(action: string) {
    console.log(`\x1b[36m[ACTION] ${action.toUpperCase()}\x1b[0m`);
    
    if (action === 'start') {
        gameState.status = 'racing';
        gameState.endTime = null;
        if (!gameState.startTime) {
            gameState.startTime = Date.now();
            console.log(`\x1b[32m[RACE] Started at ${new Date(gameState.startTime).toISOString()}\x1b[0m`);
        }
    } else if (action === 'stop') {
        gameState.status = 'stopped';
        gameState.endTime = Date.now();
        console.log(`\x1b[31m[RACE] Stopped\x1b[0m`);
    } else if (action === 'reset') {
        gameState.status = 'idle';
        gameState.startTime = null;
        gameState.endTime = null;
        
        // Reset stats for all current racers instead of reverting to initial list
        gameState.racers.forEach(r => {
            r.laps = 0;
            r.bestLap = null;
            r.lastLapTimestamp = 0;
            r.totalTime = 0;
            r.disqualified = false;
            r.checkpoints = 0;
            r.history = [];
            r.currentLapSplits = [];
            r.gap = 0;
            r.finished = false;
        });
        
        courseRecords.clear();
        console.log(`\x1b[36m[RACE] Reset - stats cleared for ${gameState.racers.length} racers\x1b[0m`);
    }
    
    server.publish("all", JSON.stringify({
        type: 'init', // Send full state on action to ensure sync
        status: gameState.status,
        racers: gameState.racers,
        startTime: gameState.startTime,
        endTime: gameState.endTime,
        totalLaps: gameState.totalLaps
    }));
}

function updateRacerGap(racer: Racer) {
    const currentTotalTime = racer.totalTime;
    
    // Update course records
    const key = `${racer.laps}-${racer.checkpoints}`;
    let recordTime = courseRecords.get(key);
    
    if (recordTime === undefined || currentTotalTime < recordTime) {
        recordTime = currentTotalTime;
        courseRecords.set(key, recordTime);
    }
    
    racer.gap = currentTotalTime - recordTime;
}

function handleLap(racerId: string) {
    if (gameState.status !== 'racing' && gameState.status !== 'finishing') return;

    const racer = gameState.racers.find(r => r.id === racerId);
    if (!racer || racer.disqualified || racer.finished) return;

    const now = Date.now();

    // Ignore the initial lap trigger (often caused by spawning on the start checkpoint).
    // Use it only to arm lap timing so the first real lap counts correctly.
    if (racer.laps === 0 && racer.lastLapTimestamp === 0) {
        racer.lastLapTimestamp = now;
        racer.checkpoints = 0;
        racer.currentLapSplits = [];
        racer.totalTime = now - (gameState.startTime || now);
        updateRacerGap(racer);

        console.log(`\x1b[33m[LAP] ${racer.name} armed lap timing (ignored warmup trigger)\x1b[0m`);

        server.publish("all", JSON.stringify({
            type: 'update',
            racers: gameState.racers,
            status: gameState.status,
            endTime: gameState.endTime
        }));
        return;
    }
    
    // Update total time from race start
    racer.totalTime = now - (gameState.startTime || now);
    
    let lapTimeStr = '--';
    if (racer.lastLapTimestamp > 0) {
        const lapTime = now - racer.lastLapTimestamp;
        lapTimeStr = (lapTime / 1000).toFixed(2) + 's';
        if (racer.bestLap === null || lapTime < racer.bestLap) {
            racer.bestLap = lapTime;
        }
        racer.history.push({
            lapNumber: racer.laps + 1,
            lapTime: lapTime,
            splits: [...racer.currentLapSplits]
        });
    }
    
    racer.laps++;
    racer.checkpoints = 0; // Reset checkpoints on lap completion
    racer.currentLapSplits = []; // Reset checkpoint times for the new lap
    racer.lastLapTimestamp = now;
    
    updateRacerGap(racer);
    
    console.log(`\x1b[33m[LAP] ${racer.name} completed lap ${racer.laps} (time: ${lapTimeStr}, total: ${(racer.totalTime / 1000).toFixed(2)}s, gap: +${(racer.gap / 1000).toFixed(2)}s)\x1b[0m`);

    checkRaceFinish(racer);

    server.publish("all", JSON.stringify({
        type: 'update',
        racers: gameState.racers,
        status: gameState.status,
        endTime: gameState.endTime
    }));
}

function getSortedRacers() {
    // Sort active racers: Most laps first, then most checkpoints, then lowest totalTime
    return [...gameState.racers]
        .filter(r => !r.disqualified)
        .sort((a, b) => {
            if (b.laps !== a.laps) return b.laps - a.laps;
            if (b.checkpoints !== a.checkpoints) return b.checkpoints - a.checkpoints;
            const timeA = a.totalTime || Infinity;
            const timeB = b.totalTime || Infinity;
            return timeA - timeB;
        });
}

function handleCheckpoint(racerId: string) {
    if (gameState.status !== 'racing' && gameState.status !== 'finishing') return;

    const racer = gameState.racers.find(r => r.id === racerId);
    if (!racer || racer.disqualified || racer.finished) return;

    const now = Date.now();
    
    // Record split time
    if (racer.lastLapTimestamp > 0) {
        const splitTime = now - racer.lastLapTimestamp;
        racer.currentLapSplits.push(splitTime);
    } else if (racer.laps === 0 && gameState.startTime) {
        // First lap splits
        const splitTime = now - gameState.startTime;
        racer.currentLapSplits.push(splitTime);
    }
    
    // Always update the racer who hit the checkpoint
    racer.checkpoints++;
    racer.totalTime = now - (gameState.startTime || now);
    
    updateRacerGap(racer);
    
    // Propagate time to lower racers if they are "faster" (time travel prevention)
    // and ensure monotonicity of time for racers behind
    const sortedRacers = getSortedRacers();
    const currentRacerIndex = sortedRacers.findIndex(r => r.id === racerId);
    
    let runningMaxTime = racer.totalTime;
    const racersToUpdate: string[] = [];

    for (let i = currentRacerIndex + 1; i < sortedRacers.length; i++) {
        const lowerRacer = sortedRacers[i];
        if (runningMaxTime > lowerRacer.totalTime) {
            lowerRacer.totalTime = runningMaxTime;
            updateRacerGap(lowerRacer);
            racersToUpdate.push(lowerRacer.name);
        } else {
            runningMaxTime = lowerRacer.totalTime;
        }
    }

    const updateMsg = racersToUpdate.length > 0 ? ` - updated times for: ${racersToUpdate.join(', ')}` : '';
    
    // Before the first real lap starts, racers often trigger the start checkpoint immediately.
    // Treat that as warmup/arming and do NOT count it as a completed lap.
    const requiredCheckpoints = racer.lastLapTimestamp === 0 ? 1 : CHECKPOINTS_PER_LAP;

    console.log(`\x1b[33m[CHECKPOINT] ${racer.name} hit checkpoint ${racer.checkpoints}/${requiredCheckpoints} (total: ${(racer.totalTime / 1000).toFixed(2)}s, gap: +${(racer.gap / 1000).toFixed(2)}s)${updateMsg}\x1b[0m`);
    
    // Auto-complete lap if checkpoints threshold reached
    if (racer.checkpoints >= requiredCheckpoints) {
        // Warmup: first start checkpoint after race start should only arm timing.
        if (racer.lastLapTimestamp === 0) {
            racer.checkpoints = 0;
            racer.currentLapSplits = [];
            racer.lastLapTimestamp = now;

            updateRacerGap(racer);
            console.log(`\x1b[33m[LAP] ${racer.name} armed lap timing (ignored warmup checkpoint)\x1b[0m`);

            server.publish("all", JSON.stringify({
                type: 'update',
                racers: gameState.racers,
                status: gameState.status,
                endTime: gameState.endTime
            }));
            return;
        }

        let lapTimeStr = '--';
        const prevTimestamp = racer.lastLapTimestamp;
        const lapTime = prevTimestamp > 0
            ? now - prevTimestamp
            : (racer.laps === 0 && gameState.startTime ? now - gameState.startTime : null);

        if (lapTime !== null) {
            lapTimeStr = `${(lapTime / 1000).toFixed(2)}s`;
            racer.history.push({
                lapNumber: racer.laps + 1,
                lapTime,
                splits: [...racer.currentLapSplits]
            });
        }

        racer.laps++;
        racer.checkpoints = 0;
        racer.currentLapSplits = [];
        racer.lastLapTimestamp = now;
        
        updateRacerGap(racer);
        
        console.log(`\x1b[33m[LAP] ${racer.name} auto-completed lap ${racer.laps} (time: ${lapTimeStr}, total: ${(racer.totalTime / 1000).toFixed(2)}s, gap: +${(racer.gap / 1000).toFixed(2)}s)\x1b[0m`);
        
        checkRaceFinish(racer);
    }

    server.publish("all", JSON.stringify({
        type: 'update',
        racers: gameState.racers,
        status: gameState.status,
        endTime: gameState.endTime
    }));
}

function handleDisqualify(racerId: string) {
    const racer = gameState.racers.find(r => r.id === racerId);
    if (!racer) return;

    racer.disqualified = !racer.disqualified; // Toggle disqualification
    console.log(`\x1b[31m[DQ] ${racer.name} ${racer.disqualified ? 'DISQUALIFIED' : 'RESTORED'}\x1b[0m`);

    server.publish("all", JSON.stringify({
        type: 'update',
        racers: gameState.racers,
        status: gameState.status,
        endTime: gameState.endTime
    }));
}

function handleRemove(name: string) {
    const index = gameState.racers.findIndex(r => r.name === name);
    if (index === -1) return;

    const racer = gameState.racers[index];
    gameState.racers.splice(index, 1);
    console.log(`\x1b[31m[RACER] Removed ${racer.name}\x1b[0m`);

    server.publish("all", JSON.stringify({
        type: 'update',
        racers: gameState.racers,
        status: gameState.status,
        endTime: gameState.endTime
    }));
}

function handleRegister(name: string) {
    if (!name) return;
    if (gameState.racers.some(r => r.name === name)) {
        console.log(`\x1b[33m[RACER] Registration failed: ${name} already exists\x1b[0m`);
        return;
    }
    
    const newRacer: Racer = {
        id: `r${Date.now()}`,
        name: name,
        avatar: '',
        laps: 0,
        bestLap: null,
        lastLapTimestamp: 0,
        totalTime: 0,
        disqualified: false,
        checkpoints: 0,
        gap: 0,
        finished: false,
        history: [],
        currentLapSplits: []
    };
    
    gameState.racers.push(newRacer);
    console.log(`\x1b[32m[RACER] Added ${newRacer.name}\x1b[0m`);
    
    server.publish("all", JSON.stringify({
        type: 'update',
        racers: gameState.racers,
        status: gameState.status,
        endTime: gameState.endTime
    }));
}

function checkRaceFinish(racer: Racer) {
    if (gameState.status === 'racing') {
        if (racer.laps >= gameState.totalLaps) {
            racer.finished = true;
            gameState.status = 'finishing';
            console.log(`\x1b[32m[RACE] ${racer.name} finished the race! Entering finishing mode.\x1b[0m`);
        }
    } else if (gameState.status === 'finishing') {
        racer.finished = true;
        console.log(`\x1b[32m[RACE] ${racer.name} finished their last lap.\x1b[0m`);
    }

    const activeRacers = gameState.racers.filter(r => !r.disqualified);
    if (activeRacers.every(r => r.finished)) {
        gameState.status = 'stopped';
        gameState.endTime = Date.now();
        console.log(`\x1b[31m[RACE] All racers finished. Race stopped.\x1b[0m`);
    }
}

console.log(`\x1b[34mListening on http://localhost:${server.port}\x1b[0m`);