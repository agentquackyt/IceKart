class Leaderboard {
    constructor(container) {
        this.container = container;
        this.items = [];
        this.queue = [];
        this.isAnimating = false;
    }

    setItems(data) {
        this.items = data;
        this.render();
    }

    swap(id1, id2) {
        this.queue.push({ id1, id2 });
        this.processQueue();
    }

    async processQueue() {
        if (this.isAnimating || this.queue.length === 0) return;

        this.isAnimating = true;
        const { id1, id2 } = this.queue.shift();

        // Find items
        const idx1 = this.items.findIndex(i => i.id === id1);
        const idx2 = this.items.findIndex(i => i.id === id2);

        if (idx1 !== -1 && idx2 !== -1) {
            // Swap in data
            const temp = this.items[idx1];
            this.items[idx1] = this.items[idx2];
            this.items[idx2] = temp;

            // Perform update with animation
            await this.update(this.items);
        }

        this.isAnimating = false;
        this.processQueue();
    }

    render() {
        const visibleData = this.items.slice(0, 8);
        const existingElements = new Map();
        Array.from(this.container.children).forEach(el => existingElements.set(el.id, el));

        // Remove elements that are no longer visible
        const visibleIds = new Set(visibleData.map(item => 'player-' + item.id));
        existingElements.forEach((el, id) => {
            if (!visibleIds.has(id)) {
                el.remove();
            }
        });

        visibleData.forEach((item, index) => {
            let el = existingElements.get('player-' + item.id);
            if (!el) {
                el = this.createItem(item, index + 1);
            } else {
                // Update content
                const posEl = el.querySelector('.position');
                const newPosText = (index + 1) + '.';
                if (posEl.textContent !== newPosText) posEl.textContent = newPosText;

                const timeEl = el.querySelector('.player-time');
                if (timeEl.textContent !== item.time) timeEl.textContent = item.time;
                
                // Update badge class
                timeEl.className = 'player-time';
                if (item.badgeType) {
                    timeEl.classList.add('badge', `badge-${item.badgeType}`);
                }
                
                // Update disqualified class on list item
                if (item.badgeType === 'disqualified') {
                    el.classList.add('disqualified');
                } else {
                    el.classList.remove('disqualified');
                }

                const nameEl = el.querySelector('.player-name');
                if (nameEl.textContent !== item.name) nameEl.textContent = item.name;

                const img = el.querySelector('.player-avatar');
                if (img && img.src !== item.avatar) {
                    img.src = item.avatar;
                }
            }

            // Only move if not in correct position
            const currentChild = this.container.children[index];
            if (currentChild !== el) {
                if (currentChild) {
                    this.container.insertBefore(el, currentChild);
                } else {
                    this.container.appendChild(el);
                }
            }
        });
    }

    createItem(item, position) {
        const li = document.createElement('li');
        li.className = 'player-list-item';
        if (item.badgeType === 'disqualified') {
            li.classList.add('disqualified');
        }
        li.id = 'player-' + item.id;
        
        const badgeClass = item.badgeType ? `badge badge-${item.badgeType}` : '';
        
        li.innerHTML = `
            <h2 class="position">${position}.</h2>
            <img src="${item.avatar}" alt="-" class="player-avatar" width="50" height="50">
            <div>
                <h3 class="player-name">${item.name}</h3>
                <h4 class="player-time ${badgeClass}">${item.time}</h4>
            </div>
        `;
        return li;
    }

    update(newData) {
        return new Promise(resolve => {
            // 1. Get current positions
            const positions = new Map();
            this.container.querySelectorAll('.player-list-item').forEach(item => {
                positions.set(item.id, item.getBoundingClientRect().top);
            });

            // 2. Update DOM
            this.items = newData;
            this.render();

            // 3. Animate
            let maxDuration = 0;
            this.container.querySelectorAll('.player-list-item').forEach(item => {
                const oldTop = positions.get(item.id);
                const newTop = item.getBoundingClientRect().top;

                if (oldTop !== undefined) {
                    const delta = oldTop - newTop;
                    if (delta !== 0) {
                        maxDuration = 1200;
                        const isMovingUp = delta > 0; // Moved from lower (higher Y) to higher (lower Y)

                        // Setup initial state (inverted)
                        item.style.transition = 'none';
                        item.style.transform = `translateY(${delta}px) ${isMovingUp ? 'scale(1.1)' : 'scale(1)'}`;
                        if (isMovingUp) {
                            item.style.zIndex = 100;
                            item.style.boxShadow = '0 5px 15px rgba(0,0,0,0.3)';
                            item.style.position = 'relative';
                        } else {
                            item.style.zIndex = 1;
                            item.style.position = 'relative';
                        }

                        // Force reflow
                        item.getBoundingClientRect();

                        // Animate to final
                        requestAnimationFrame(() => {
                            item.style.transition = 'transform 1.2s cubic-bezier(0.34, 1.56, 0.64, 1)'; // Bouncy
                            item.style.transform = 'translateY(0) scale(1)';

                            // Cleanup
                            setTimeout(() => {
                                item.style.transition = '';
                                item.style.transform = '';
                                item.style.zIndex = '';
                                item.style.boxShadow = '';
                                item.style.position = '';
                            }, 1200);
                        });
                    }
                } else {
                    // New item
                    item.style.animation = 'slideIn 0.5s forwards';
                    item.addEventListener('animationend', () => {
                        item.style.animation = '';
                    }, { once: true });
                }
            });

            setTimeout(resolve, maxDuration);
        });
    }
}

// Setup
document.addEventListener('DOMContentLoaded', () => {
    const list = document.querySelector('.list-view');
    window.leaderboard = new Leaderboard(list);
});
