/** Reference renderer (TZ §3): SVG lines + absolute cards from createTree output. */
const GRID_PX = 40;
const NODE_UNITS = 2;

export function renderTree(container, familyData, rootId, displayById = {}) {
  const tree = createTree(familyData, { rootId });
  const width = tree.canvas.width * GRID_PX;
  const height = tree.canvas.height * GRID_PX;

  container.innerHTML = '';
  container.style.width = `${width}px`;
  container.style.height = `${height}px`;

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', 'tree-lines');
  svg.setAttribute('width', width);
  svg.setAttribute('height', height);
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`);

  tree.connectors.forEach(([x1, y1, x2, y2]) => {
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', x1 * GRID_PX);
    line.setAttribute('y1', y1 * GRID_PX);
    line.setAttribute('x2', x2 * GRID_PX);
    line.setAttribute('y2', y2 * GRID_PX);
    line.setAttribute('stroke', '#6b8cae');
    line.setAttribute('stroke-width', '2');
    svg.appendChild(line);
  });

  const cards = document.createElement('div');
  cards.className = 'tree-cards';
  cards.style.width = `${width}px`;
  cards.style.height = `${height}px`;

  tree.nodes.forEach((node) => {
    const card = document.createElement('div');
    const genderClass =
      node.gender === 'female' ? 'female' : node.gender === 'male' ? 'male' : 'other';
    card.className = `person-card ${genderClass}`;
    card.style.left = `${node.left * GRID_PX}px`;
    card.style.top = `${node.top * GRID_PX}px`;
    card.style.width = `${NODE_UNITS * GRID_PX}px`;
    card.style.height = `${NODE_UNITS * GRID_PX}px`;
    const info = displayById[node.id] || { firstName: node.id, lastName: '' };
    card.innerHTML = `<div class="name">${info.firstName} ${info.lastName}</div>`;
    cards.appendChild(card);
  });

  container.appendChild(svg);
  container.appendChild(cards);
}
