const ws = new WebSocket(`ws://${window.location.host}/ws`);
const grid = document.getElementById('racer-grid');
const statusDisplay = document.getElementById('status-display');

ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.type === 'init' || data.type === 'update') {
        renderRacers(data.racers);
        if (data.status) updateStatus(data.status);
    }
};

function updateStatus(status) {
    statusDisplay.textContent = status.toUpperCase();
    statusDisplay.style.color = status === 'racing' ? '#2ecc71' : '#fff';
}

function renderRacers(racers) {
    grid.innerHTML = '';
    racers.forEach(racer => {
        const card = document.createElement('div');
        card.className = 'racer-card';
        if (racer.disqualified) {
            card.classList.add('disqualified');
        }
        
        const bestLap = racer.bestLap ? (racer.bestLap / 1000).toFixed(2) + 's' : '--';
        const dqBtnText = racer.disqualified ? 'RESTORE' : 'DQ';
        const dqBtnClass = racer.disqualified ? 'btn-green' : 'btn-gray';
        
        // Use avatar from racer object if available, otherwise fallback
        const avatarUrl = racer.avatar || `https://minotar.net/armor/bust/${racer.name}/50.png`;

        card.innerHTML = `
            <img src="${avatarUrl}" class="racer-avatar" alt="${racer.name}">
            <div class="racer-info">
                <div class="racer-name">${racer.name}</div>
                <div class="racer-stats">Laps: ${racer.laps} | CP: ${racer.checkpoints || 0} | Best: ${bestLap}</div>
            </div>
            <div class="racer-actions">
                <button class="btn btn-small btn-checkpoint" onclick="sendCheckpoint('${racer.id}')">+ CP</button>
                <button class="btn btn-small btn-dq ${dqBtnClass}" onclick="sendDisqualify('${racer.id}')">${dqBtnText}</button>
                <button class="btn btn-small btn-red" onclick="sendRemove('${racer.name}')">DEL</button>
            </div>
        `;
        grid.appendChild(card);
    });
}

function sendLap(racerId) {
    ws.send(JSON.stringify({ type: 'lap', racerId }));
}

function sendCheckpoint(racerId) {
    ws.send(JSON.stringify({ type: 'checkpoint', racerId }));
}

function sendDisqualify(racerId) {
    ws.send(JSON.stringify({ type: 'disqualify', racerId }));
}

function sendRemove(name) {
    if (confirm(`Are you sure you want to remove ${name}?`)) {
        ws.send(JSON.stringify({ type: 'remove', name }));
    }
}

document.getElementById('btn-start').onclick = () => ws.send(JSON.stringify({ type: 'action', payload: 'start' }));
document.getElementById('btn-stop').onclick = () => ws.send(JSON.stringify({ type: 'action', payload: 'stop' }));
document.getElementById('btn-reset').onclick = () => ws.send(JSON.stringify({ type: 'action', payload: 'reset' }));

document.getElementById('btn-register').onclick = async () => {
    const nameInput = document.getElementById('new-racer-name');
    const name = nameInput.value.trim();
    
    if (!name) return;
    
    try {
        const response = await fetch('/api/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ name })
        });
        
        if (response.ok) {
            nameInput.value = '';
        } else {
            alert('Failed to register racer');
        }
    } catch (e) {
        console.error(e);
        alert('Error registering racer');
    }
};
