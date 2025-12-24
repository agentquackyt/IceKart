const canvas = document.getElementById('snow-canvas');
const ctx = canvas.getContext('2d');
let snowPieces = [];
let animationId = null;

function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
}

window.addEventListener('resize', resizeCanvas);
resizeCanvas();

class SnowPiece {
    constructor() {
        this.reset(true);
    }

    reset(initial = false) {
        this.x = Math.random() * canvas.width;
        this.y = initial ? Math.random() * canvas.height : -10;
        this.size = Math.random() * 5 + 2; // Smaller than confetti
        this.speedY = Math.random() * 1 + 0.5; // Slower falling
        this.speedX = Math.random() * 1 - 0.5; // Slight drift
        this.rotation = Math.random() * 360;
        this.rotationSpeed = Math.random() * 2 - 1;
        this.color = this.getRandomColor();
        this.opacity = Math.random() * 0.5 + 0.5;
    }
    
    getRandomColor() {
        const colors = [
            '#ffffff',            // White
            '#e0f7fa',            // Very Light Blue
            '#b2ebf2',            // Light Blue
            '#81d4fa',            // Sky Blue
        ];
        return colors[Math.floor(Math.random() * colors.length)];
    }
    
    update() {
        this.y += this.speedY;
        this.x += this.speedX;
        this.rotation += this.rotationSpeed;
        
        // Wrap around
        if (this.y > canvas.height) {
            this.reset();
        }
        if (this.x > canvas.width) {
            this.x = 0;
        } else if (this.x < 0) {
            this.x = canvas.width;
        }
    }
    
    draw() {
        ctx.save();
        ctx.translate(this.x, this.y);
        ctx.rotate((this.rotation * Math.PI) / 180);
        ctx.globalAlpha = this.opacity;
        ctx.fillStyle = this.color;
        // Draw circle for snow effect
        ctx.beginPath();
        ctx.arc(0, 0, this.size, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }
}

function startSnow() {
    snowPieces = [];
    for (let i = 0; i < 200; i++) {
        snowPieces.push(new SnowPiece());
    }
    animateSnow();
}

function animateSnow() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    
    snowPieces.forEach(piece => {
        piece.update();
        piece.draw();
    });
    
    animationId = requestAnimationFrame(animateSnow);
}

// Start immediately
startSnow();
