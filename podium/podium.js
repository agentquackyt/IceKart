// Fetch race results
let allRacers = [];
let animationPlayed = false;

async function init() {
    try {
        const response = await fetch('/api/results');
        const data = await response.json();
        allRacers = data.racers;
        
        if (allRacers.length > 0 && !animationPlayed) {
            animationPlayed = true;
            showPodium();
        } else if (allRacers.length === 0) {
            // No racers, show empty results
            showResults();
        }
    } catch (e) {
        console.error('Failed to fetch results:', e);
    }
}

function formatTime(ms) {
    if (!ms) return '--';
    const minutes = Math.floor(ms / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    const centis = Math.floor((ms % 1000) / 10);
    return `${minutes}:${seconds.toString().padStart(2, '0')}.${centis.toString().padStart(2, '0')}`;
}

function showPodium() {
    const podiumView = document.getElementById('podium-view');
    const top3 = allRacers.slice(0, 3);
    
    // Populate podium data
    top3.forEach((racer, index) => {
        const place = index + 1;
        const element = document.getElementById(`place-${place}`);
        if (!element) return;
        
        const avatar = element.querySelector('.racer-avatar');
        const name = element.querySelector('.racer-name');
        const time = element.querySelector('.racer-time');
        
        avatar.src = `https://minotar.net/armor/body/${racer.name}/150.png`;
        avatar.alt = racer.name;
        name.textContent = racer.name;
        time.textContent = formatTime(racer.totalTime);
    });
    
    // Hide empty podium spots
    for (let i = top3.length + 1; i <= 3; i++) {
        const element = document.getElementById(`place-${i}`);
        if (element) element.style.display = 'none';
    }
    
    // Animate podium entries
    setTimeout(() => {
        const third = document.getElementById('place-3');
        if (third && top3.length >= 3) third.classList.add('animate-in');
    }, 1000);
    
    setTimeout(() => {
        const second = document.getElementById('place-2');
        if (second && top3.length >= 2) second.classList.add('animate-in');
    }, 2000);
    
    setTimeout(() => {
        const first = document.getElementById('place-1');
        if (first && top3.length >= 1) {
            first.classList.add('animate-in');
            // Start confetti when winner appears
            if (top3.length > 0) {
                startConfetti();
            }
        }
    }, 3000);
    
    // Transition to results after animation
    setTimeout(() => {
        stopConfetti();
        showResults();
    }, 21000);
}

function showResults() {
    const podiumView = document.getElementById('podium-view');
    const resultsView = document.getElementById('results-view');
    const resultsBody = document.getElementById('results-body');
    
    podiumView.classList.add('hidden');
    resultsView.classList.remove('hidden');
    
    // Populate results table
    resultsBody.innerHTML = '';
    allRacers.forEach((racer, index) => {
        const row = document.createElement('tr');
        if (racer.disqualified) row.classList.add('disqualified');
        
        row.innerHTML = `
            <td>${index + 1}</td>
            <td>
                <div class="racer-cell">
                    <img src="https://minotar.net/armor/bust/${racer.name}/70.png" alt="${racer.name}">
                    <span>${racer.name}</span>
                </div>
            </td>
            <td>${racer.laps}</td>
            <td>${racer.bestLap ? formatTime(racer.bestLap) : '--'}</td>
            <td>${formatTime(racer.totalTime)}</td>
        `;
        resultsBody.appendChild(row);
    });
}

// Confetti implementation
const canvas = document.getElementById('confetti-canvas');
const ctx = canvas.getContext('2d');
let confettiPieces = [];
let confettiAnimationId = null;

function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
}

window.addEventListener('resize', resizeCanvas);
resizeCanvas();

class ConfettiPiece {
    constructor() {
        this.x = Math.random() * canvas.width;
        this.y = Math.random() * canvas.height - canvas.height;
        this.size = Math.random() * 10 + 5;
        this.speedY = Math.random() * 3 + 2;
        this.speedX = Math.random() * 2 - 1;
        this.rotation = Math.random() * 360;
        this.rotationSpeed = Math.random() * 10 - 5;
        this.color = this.getRandomColor();
    }
    
    getRandomColor() {
        const colors = [
            'rgb(215, 166, 70)',  // Gold
            'rgb(160, 156, 156)', // Silver
            'rgb(201, 137, 27)',  // Bronze
            '#3498db',            // Blue
            '#2ecc71',            // Green
            '#e74c3c',            // Red
            '#9b59b6',            // Purple
            '#f39c12',            // Orange
            '#1abc9c',            // Teal
        ];
        return colors[Math.floor(Math.random() * colors.length)];
    }
    
    update() {
        this.y += this.speedY;
        this.x += this.speedX;
        this.rotation += this.rotationSpeed;
        
        if (this.y > canvas.height) {
            this.y = -this.size;
            this.x = Math.random() * canvas.width;
        }
    }
    
    draw() {
        ctx.save();
        ctx.translate(this.x, this.y);
        ctx.rotate((this.rotation * Math.PI) / 180);
        ctx.fillStyle = this.color;
        ctx.fillRect(-this.size / 2, -this.size / 2, this.size, this.size / 2);
        ctx.restore();
    }
}

function startConfetti() {
    confettiPieces = [];
    for (let i = 0; i < 150; i++) {
        confettiPieces.push(new ConfettiPiece());
    }
    animateConfetti();
}

function animateConfetti() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    confettiPieces.forEach(piece => {
        piece.update();
        piece.draw();
    });
    
    confettiAnimationId = requestAnimationFrame(animateConfetti);
}

function stopConfetti() {
    if (confettiAnimationId) {
        cancelAnimationFrame(confettiAnimationId);
        confettiAnimationId = null;
    }
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    confettiPieces = [];
}

// Initialize
init();
