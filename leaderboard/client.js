const ws = new WebSocket(`ws://${window.location.host}/ws`);

let raceStartTime = null;
let raceEndTime = null;
let raceStatus = 'idle';
let timerInterval = null;

ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    
    if (data.type === 'init') {
        raceStatus = data.status;
        raceStartTime = data.startTime;
        raceEndTime = data.endTime || null;
        updateLeaderboard(data.racers);
        updateGameInfo(data.racers, data.totalLaps);
        handleTimer();
    } else if (data.type === 'update') {
        if (data.status && data.status !== raceStatus) {
            raceStatus = data.status;
            if (data.endTime) raceEndTime = data.endTime;
            handleTimer();
        }
        
        updateLeaderboard(data.racers);
        // We don't have totalLaps in update, so we might need to store it or assume it hasn't changed
        // Ideally update should include it or we store it globally from init
        // For now, let's assume totalLaps is constant or we need to fetch it.
        // Actually, let's just update laps based on racers.
        updateLapsDisplay(data.racers);
    }
};

function handleTimer() {
    if (timerInterval) clearInterval(timerInterval);
    
    if ((raceStatus === 'racing' || raceStatus === 'finishing') && raceStartTime) {
        timerInterval = setInterval(() => {
            const elapsed = Date.now() - raceStartTime;
            updateTimeDisplay(elapsed);
        }, 100);
    } else if (raceStatus === 'stopped' && raceStartTime && raceEndTime) {
        const elapsed = raceEndTime - raceStartTime;
        updateTimeDisplay(elapsed);
    } else if (raceStatus === 'idle') {
        updateTimeDisplay(0);
    }
}

function updateGameInfo(racers, totalLaps) {
    // Store totalLaps globally if needed, or just update DOM
    window.totalLaps = totalLaps;
    updateLapsDisplay(racers);
}

function updateLapsDisplay(racers) {
    const lapEl = document.querySelector('.lap-count h2');
    if (!lapEl) return;

    if (raceStatus === 'finishing' || raceStatus === 'stopped') {
        lapEl.textContent = '🏁';
        return;
    }

    const maxLaps = racers.length > 0 ? Math.max(...racers.map(r => r.laps)) : 0;
    // Current lap is max completed laps + 1, capped at total
    let currentLap = maxLaps;
    const total = window.totalLaps || 15;
    
    if (currentLap > total) currentLap = total;
    
    lapEl.textContent = `${currentLap}/${total}`;
}

function updateTimeDisplay(ms) {
    const timeEl = document.querySelector('.time-count h2');
    if (timeEl) {
        // Format: H:MM:SS or M:SS:CC?
        // HTML example: 0:12:12 -> likely M:SS:CC or H:MM:SS
        // Let's assume M:SS:CC matching the leaderboard format
        // Actually HTML example 0:12:12 looks like H:MM:SS or M:SS:CC
        // Let's use a standard race timer format
        
        const minutes = Math.floor(ms / 60000);
        const seconds = Math.floor((ms % 60000) / 1000);
        const centis = Math.floor((ms % 1000) / 10);
        
        timeEl.textContent = `${minutes}:${seconds.toString().padStart(2, '0')}:${centis.toString().padStart(2, '0')}`;
    }
}

function updateLeaderboard(racers) {
    if (!window.leaderboard) return;

    // Separate active and disqualified racers
    const activeRacers = racers.filter(r => !r.disqualified);
    const disqualifiedRacers = racers.filter(r => r.disqualified);

    // Sort active racers: Most laps first, then most checkpoints, then lowest totalTime
    const sortedActive = [...activeRacers].sort((a, b) => {
        if (b.laps !== a.laps) return b.laps - a.laps;
        if (b.checkpoints !== a.checkpoints) return b.checkpoints - a.checkpoints;
        // If laps and checkpoints are equal, faster total time wins (lower is better)
        const timeA = a.totalTime || Infinity;
        const timeB = b.totalTime || Infinity;
        return timeA - timeB;
    });

    // Disqualified go to the end
    const sortedRacers = [...sortedActive, ...disqualifiedRacers];
    
    // Get leader info for calculating differences
    const leader = sortedActive[0];
    const leaderLaps = leader ? leader.laps : 0;
    const leaderTime = leader ? leader.totalTime : 0;

    const transformedData = sortedRacers.map((r, index) => {
        let timeDisplay;
        let badgeType = null; // 'laps' for blue badge, 'disqualified' for gray badge
        
        if (r.disqualified) {
            timeDisplay = 'Disqualified';
            badgeType = 'disqualified';
        } else if (index === 0) {
            timeDisplay = 'Leader';
        } else {
            const lapDiff = leaderLaps - r.laps;
            // Only show "+N laps" badge if difference is 2 or more laps
            if (lapDiff >= 2) {
                // Show lap difference with blue badge (but display lapDiff - 1 since they're "lapped")
                const displayLaps = lapDiff - 1;
                timeDisplay = `+${displayLaps} lap${displayLaps > 1 ? 's' : ''}`;
                badgeType = 'laps';
            } else {
                // Same lap count, show time difference
                // Use server-provided gap if available, otherwise fallback to calculated diff
                const timeDiff = (r.gap !== undefined) ? r.gap : (r.totalTime - leaderTime);
                timeDisplay = `+${formatTimeDiff(timeDiff)}`;
            }
        }
        
        return {
            id: r.id,
            name: r.name,
            avatar: `https://minotar.net/armor/bust/${r.name}/50.png`,
            time: timeDisplay,
            badgeType: badgeType
        };
    });

    window.leaderboard.update(transformedData);
}

function formatTimeDiff(ms) {
    if (!ms || ms <= 0) return '0.00';
    const seconds = Math.floor(ms / 1000);
    const centis = Math.floor((ms % 1000) / 10);
    if (seconds >= 60) {
        const minutes = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${minutes}:${secs.toString().padStart(2, '0')}.${centis.toString().padStart(2, '0')}`;
    }
    return `${seconds}.${centis.toString().padStart(2, '0')}`;
}

function formatTime(ms) {
    if (!ms) return '--:--.--';
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    const centis = Math.floor((ms % 1000) / 10);
    return `${minutes}:${seconds.toString().padStart(2, '0')}.${centis.toString().padStart(2, '0')}`;
}
