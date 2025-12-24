// WebSocket and Racer Logic
const ws = new WebSocket(`ws://${window.location.host}/ws`);
const urlParams = new URLSearchParams(window.location.search);
const targetRacerName = urlParams.get('name');

let totalLaps = 3;
let checkpointsPerLap = 10;
let raceStartTime = null;
let raceStatus = 'idle';
let timerInterval = null;

if (!targetRacerName) {
    document.getElementById('not-found').innerHTML = '<h2>No racer specified</h2><p>Please add ?name=RacerName to the URL</p>';
    document.getElementById('not-found').classList.remove('hidden');
}

ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    
    if (data.type === 'init') {
        totalLaps = data.totalLaps;
        raceStartTime = data.startTime;
        raceStatus = data.status;
        updateRacerInfo(data.racers);
        handleTimer();
    } else if (data.type === 'update') {
        if (data.status && data.status !== raceStatus) {
            raceStatus = data.status;
            if (data.startTime) raceStartTime = data.startTime;
            handleTimer();
        }
        updateRacerInfo(data.racers);
    }
};

function handleTimer() {
    if (timerInterval) clearInterval(timerInterval);
    
    if ((raceStatus === 'racing' || raceStatus === 'finishing') && raceStartTime) {
        timerInterval = setInterval(() => {
            const elapsed = Date.now() - raceStartTime;
            document.getElementById('racer-total-time').textContent = formatTime(elapsed);
        }, 50);
    } else if (raceStatus === 'idle') {
        document.getElementById('racer-total-time').textContent = formatTime(0);
    }
}

function formatTime(ms) {
    if (!ms && ms !== 0) return '--:--.--';
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    const centis = Math.floor((ms % 1000) / 10);
    return `${minutes}:${seconds.toString().padStart(2, '0')}.${centis.toString().padStart(2, '0')}`;
}

function updateRacerInfo(racers) {
    if (!targetRacerName) return;

    const sortedRacers = [...racers]
        .filter(r => !r.disqualified)
        .sort((a, b) => {
            if (b.laps !== a.laps) return b.laps - a.laps;
            if (b.checkpoints !== a.checkpoints) return b.checkpoints - a.checkpoints;
            const timeA = a.totalTime || Infinity;
            const timeB = b.totalTime || Infinity;
            return timeA - timeB;
        });

    const racer = racers.find(r => r.name.toLowerCase() === targetRacerName.toLowerCase());
    
    if (!racer) {
        document.getElementById('racer-info').classList.add('hidden');
        document.getElementById('not-found').classList.remove('hidden');
        return;
    }

    document.getElementById('racer-info').classList.remove('hidden');
    document.getElementById('not-found').classList.add('hidden');

    // Name
    document.getElementById('racer-name').textContent = racer.name;
    
    // Avatar
    const avatar = document.getElementById('racer-avatar');
    const avatarUrl = racer.avatar || `https://minotar.net/armor/body/${racer.name}/280.png`;
    if (avatar.src !== avatarUrl) avatar.src = avatarUrl;
    
    // Position
    const position = sortedRacers.findIndex(r => r.id === racer.id) + 1;
    const posEl = document.getElementById('racer-position');
    posEl.textContent = racer.disqualified ? 'DQ' : (position > 0 ? position : '-');
    posEl.className = 'position-value';
    if (position === 1) posEl.classList.add('pos-1');
    else if (position === 2) posEl.classList.add('pos-2');
    else if (position === 3) posEl.classList.add('pos-3');

    // Stats
    document.getElementById('racer-laps').textContent = `${racer.laps} / ${totalLaps}`;
    document.getElementById('racer-checkpoints').textContent = `${racer.checkpoints || 0} / ${checkpointsPerLap}`;
    document.getElementById('racer-best-lap').textContent = formatTime(racer.bestLap);
    
    // Gap
    const gapEl = document.getElementById('racer-gap');
    if (position === 1 && !racer.disqualified) {
        gapEl.textContent = 'Leader';
    } else if (racer.disqualified) {
        gapEl.textContent = 'DQ';
    } else {
        gapEl.textContent = racer.gap ? `+${(racer.gap / 1000).toFixed(2)}s` : '-';
    }

    // Total Time
    if (racer.finished || raceStatus === 'stopped') {
        if (timerInterval) clearInterval(timerInterval);
        document.getElementById('racer-total-time').textContent = formatTime(racer.totalTime);
    }

    // Status
    const statusEl = document.getElementById('racer-status');
    statusEl.className = 'status-badge';
    if (racer.disqualified) {
        statusEl.textContent = 'DISQUALIFIED';
        statusEl.classList.add('status-disqualified');
    } else if (racer.finished) {
        statusEl.textContent = 'FINISHED';
        statusEl.classList.add('status-finished');
    } else if (raceStatus === 'racing' || raceStatus === 'finishing') {
        statusEl.textContent = 'RACING';
        statusEl.classList.add('status-racing');
    } else {
        statusEl.textContent = 'READY';
    }

    // Lap Times Table
    updateMergedTable(racer);
}

function updateMergedTable(racer) {
    const thead = document.getElementById('merged-table-head');
    const tbody = document.getElementById('merged-table-body');
    
    // Generate headers if needed
    const headerRow = thead.rows[0];
    // Expected columns: Lap + checkpointsPerLap + Time
    if (headerRow.cells.length !== 2 + checkpointsPerLap) {
        let html = '<th>Lap</th>';
        for (let i = 1; i <= checkpointsPerLap; i++) {
            html += `<th>CP ${i}</th>`;
        }
        html += '<th>Time</th>';
        headerRow.innerHTML = html;
    }

    let html = '';
    
    // History rows
    if (racer.history && racer.history.length > 0) {
        racer.history.forEach(lapData => {
            const isBest = racer.bestLap === lapData.lapTime;
            html += `<tr class="${isBest ? 'best' : ''}">
                <td>${lapData.lapNumber}</td>`;
            
            // Checkpoints
            let prevSplit = 0;
            for (let i = 0; i < checkpointsPerLap; i++) {
                const split = lapData.splits[i];
                if (split !== undefined) {
                    // Calculate segment time (split - prevSplit)
                    const segment = split - prevSplit;
                    html += `<td>${formatTime(segment)}</td>`;
                    prevSplit = split;
                } else {
                    html += `<td>-</td>`;
                }
            }
            
            html += `<td>${formatTime(lapData.lapTime)}</td></tr>`;
        });
    }

    // Current Lap row
    if (!racer.finished && !racer.disqualified) {
        const currentLapNum = racer.laps + 1;
        if (currentLapNum <= totalLaps) {
             html += `<tr class="current-lap">
                <td>${currentLapNum}</td>`;
            
            let prevSplit = 0;
            for (let i = 0; i < checkpointsPerLap; i++) {
                const split = racer.currentLapSplits ? racer.currentLapSplits[i] : undefined;
                if (split !== undefined) {
                    const segment = split - prevSplit;
                    html += `<td>${formatTime(segment)}</td>`;
                    prevSplit = split;
                } else {
                    html += `<td>-</td>`;
                }
            }
            html += `<td>Running</td></tr>`;
        }
    }

    if (html === '') {
        html = '<tr class="empty-row"><td colspan="100%">No laps started</td></tr>';
    }

    tbody.innerHTML = html;
}
