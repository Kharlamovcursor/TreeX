(() => {
  // vendor/relatives-tree/lib/utils/index.js
  var nextIndex = (index) => index + 1;
  var prop = (name) => (item) => item[name];
  var withId = (id) => (item) => item.id === id;
  var withIds = (ids, include = true) => (item) => ids.includes(item.id) === include;
  var unique = (item, index, arr) => arr.indexOf(item) === index;
  var inAscOrder = (v1, v2) => v1 - v2;
  var pipe = (...fus) => (init) => fus.reduce((res, fn) => fn(res), init);
  var min = (arr) => Math.min.apply(null, arr);
  var max = (arr) => Math.max.apply(null, arr);
  var toMap = (items) => new Map(items.map((item) => [item.id, Object.assign({}, item)]));
  var hasDiffParents = (node) => node.parents.map(prop("type")).filter(unique).length > 1;
  var byGender = (target) => (_, b) => b.gender !== target ? -1 : 1;
  var relToNode = (store) => (rel) => store.getNode(rel.id);
  var withRelType = (...types) => (item) => types.includes(item.type);

  // vendor/relatives-tree/lib/constants.js
  var SIZE = 2;
  var HALF_SIZE = SIZE / 2;
  var NODES_IN_COUPLE = 2;

  // vendor/relatives-tree/lib/utils/units.js
  var newUnit = (fid, nodes, isChild = false) => ({
    fid,
    child: isChild,
    nodes: [...nodes],
    pos: 0
  });
  var nodeIds = (unit) => unit.nodes.map(prop("id"));
  var nodeCount = (unit) => unit.nodes.length;
  var hasChildren = (unit) => unit.nodes.some((node) => node.children.length);
  var rightSide = (unit) => unit.pos + nodeCount(unit) * SIZE;
  var sameAs = (target) => (unit) => nodeIds(target).join("") === nodeIds(unit).join("");
  var getUnitX = (family, unit) => family.X + unit.pos;
  var unitsToNodes = (units) => units.map(prop("nodes")).flat();
  var arrangeInOrder = (units) => {
    units.forEach((unit, idx, self) => unit.pos = idx === 0 ? 0 : rightSide(self[idx - 1]));
  };
  var correctUnitsShift = (units, shift) => units.forEach((unit) => unit.pos += shift);

  // vendor/relatives-tree/lib/utils/family.js
  var newFamily = (id, type, main = false) => ({
    id,
    type,
    main,
    Y: 0,
    X: 0,
    parents: [],
    children: []
  });
  var withType = (...types) => (item) => types.includes(item.type);
  var widthOf = (family) => max([...family.parents, ...family.children].map(rightSide));
  var heightOf = (family) => [family.parents.length, family.children.length].filter(Boolean).length * SIZE;
  var rightOf = (family) => family.X + widthOf(family);
  var bottomOf = (family) => family.Y + heightOf(family);
  var unitNodesCount = (units) => units.reduce((acc, b) => acc + nodeCount(b), 0);
  var getParentsX = (family, unit) => {
    return unit ? getUnitX(family, unit) + nodeCount(unit) : 0;
  };

  // vendor/relatives-tree/lib/types.js
  var Gender;
  (function(Gender2) {
    Gender2["male"] = "male";
    Gender2["female"] = "female";
  })(Gender || (Gender = {}));
  var RelType;
  (function(RelType2) {
    RelType2["blood"] = "blood";
    RelType2["married"] = "married";
    RelType2["divorced"] = "divorced";
    RelType2["adopted"] = "adopted";
    RelType2["half"] = "half";
  })(RelType || (RelType = {}));
  var FamilyType;
  (function(FamilyType2) {
    FamilyType2["root"] = "root";
    FamilyType2["child"] = "child";
    FamilyType2["parent"] = "parent";
  })(FamilyType || (FamilyType = {}));

  // vendor/relatives-tree/lib/store.js
  var Store = class {
    constructor(nodes, rootId) {
      if (!nodes.find(withId(rootId)))
        throw new ReferenceError();
      this.nextId = 0;
      this.families = /* @__PURE__ */ new Map();
      this.nodes = toMap(nodes);
      this.root = this.nodes.get(rootId);
    }
    getNextId() {
      return ++this.nextId;
    }
    getNode(id) {
      return this.nodes.get(id);
    }
    getNodes(ids) {
      return ids.map((id) => this.getNode(id));
    }
    getFamily(id) {
      return this.families.get(id);
    }
    get familiesArray() {
      return [...this.families.values()];
    }
    get rootFamilies() {
      return this.familiesArray.filter(withType(FamilyType.root));
    }
    get rootFamily() {
      return this.rootFamilies.find((family) => family.main);
    }
  };
  var store_default = Store;

  // vendor/relatives-tree/lib/middle/placeholders.js
  var createRel = (id, type = RelType.blood) => ({ id, type });
  var createNode = (gender) => ({
    id: `${gender}-ph`,
    placeholder: true,
    gender,
    parents: [],
    siblings: [],
    spouses: [],
    children: []
  });
  var createParents = (store) => {
    const father = createNode(Gender.male);
    const mother = createNode(Gender.female);
    father.spouses = [createRel(mother.id, RelType.married)];
    mother.spouses = [createRel(father.id, RelType.married)];
    return [father, mother].map((node) => {
      node.children = store.root.siblings.concat(createRel(store.root.id));
      store.nodes.set(node.id, node);
      return createRel(node.id);
    });
  };
  var setParents = (parents2) => (node) => node.parents = parents2.slice();
  var placeholders = (store) => {
    if (!store.root.parents.length) {
      const setParentsTo = setParents(createParents(store));
      setParentsTo(store.root);
      store.root.siblings.map(relToNode(store)).forEach(setParentsTo);
    }
    return store;
  };

  // vendor/relatives-tree/lib/utils/getSpouseNodesFunc.js
  var inDescOrderOfChildCount = (a, b) => b.children.length - a.children.length;
  var getSpouse = (store, spouses) => {
    const toNode = relToNode(store);
    const married = spouses.find(withRelType(RelType.married));
    if (married)
      return toNode(married);
    if (spouses.length >= 1)
      return spouses.map(toNode).sort(inDescOrderOfChildCount)[0];
    return;
  };
  var getCoupleNodes = (store, target) => {
    return [target, getSpouse(store, target.spouses)].filter((node) => Boolean(node)).sort(byGender(store.root.gender));
  };
  var excludeRel = (target) => (rel) => rel.id !== target.id;
  var getSpouseNodesFunc = (store) => {
    const toNode = relToNode(store);
    return (parents2) => {
      let middle3 = parents2;
      if (middle3.length !== NODES_IN_COUPLE)
        middle3 = getCoupleNodes(store, middle3[0]);
      const result = { left: [], middle: middle3, right: [] };
      if (middle3.length === NODES_IN_COUPLE) {
        const [first, second] = middle3;
        result.left = first.spouses.filter(excludeRel(second)).map(toNode);
        result.right = second.spouses.filter(excludeRel(first)).map(toNode);
      }
      return result;
    };
  };

  // vendor/relatives-tree/lib/utils/createChildUnitsFunc.js
  var toArray = (nodes) => nodes.map((node) => Array.of(node));
  var createChildUnitsFunc = (store) => {
    const getSpouseNodes = getSpouseNodesFunc(store);
    return (familyId, child) => {
      const { left, middle: middle3, right } = getSpouseNodes([child]);
      return [...toArray(left), middle3, ...toArray(right)].map((nodes) => newUnit(familyId, nodes, true));
    };
  };

  // vendor/relatives-tree/lib/utils/setDefaultUnitShift.js
  var arrangeUnitsIn = (family) => {
    const diff = unitNodesCount(family.parents) - unitNodesCount(family.children);
    if (diff === 0)
      return;
    if (diff > 0)
      correctUnitsShift(family.children, diff);
    else
      correctUnitsShift(family.parents, Math.abs(diff));
  };
  var setDefaultUnitShift = (family) => {
    const units = [family.parents, family.children];
    units.forEach(arrangeInOrder);
    arrangeUnitsIn(family);
    const start = min(units.flat().map(prop("pos")));
    if (start !== 0)
      units.forEach((items) => correctUnitsShift(items, start * -1));
  };

  // vendor/relatives-tree/lib/children/create.js
  var hasSameRelation = (node) => (rel) => !node || node.children.some(withId(rel.id));
  var getChildUnitsFunc = (store) => {
    const toNode = relToNode(store);
    const createChildUnits = createChildUnitsFunc(store);
    return (familyId, parents2) => {
      const [first, second] = parents2;
      return first.children.filter(hasSameRelation(second)).flatMap((rel) => createChildUnits(familyId, toNode(rel)));
    };
  };
  var createFamilyFunc = (store) => {
    const getChildUnits = getChildUnitsFunc(store);
    return (parentIDs, type = FamilyType.root, isMain = false) => {
      const family = newFamily(store.getNextId(), type, isMain);
      const parents2 = parentIDs.map((id) => store.getNode(id)).sort(byGender(store.root.gender));
      family.parents = [newUnit(family.id, parents2)];
      family.children = getChildUnits(family.id, parents2);
      setDefaultUnitShift(family);
      return family;
    };
  };

  // vendor/relatives-tree/lib/middle/correctOverlaps.js
  var moveSharedUnitToRight = (sharedIDs) => (a, b) => {
    const foundA = a.nodes.some(withIds(sharedIDs));
    const foundB = b.nodes.some(withIds(sharedIDs));
    if (foundA && !foundB)
      return 1;
    if (!foundA && foundB)
      return -1;
    return 0;
  };
  var sameIn = (units) => {
    const target = unitsToNodes(units);
    return (node) => target.some(withId(node.id));
  };
  var correctOverlaps = (bloodFamily, adoptedFamily) => {
    const sharedIDs = unitsToNodes(bloodFamily.children).filter(sameIn(adoptedFamily.children)).map(prop("id"));
    const cachePos = bloodFamily.children.map(prop("pos"));
    bloodFamily.children = [...bloodFamily.children].sort(moveSharedUnitToRight(sharedIDs));
    bloodFamily.children.forEach((unit, idx) => unit.pos = cachePos[idx]);
    adoptedFamily.children = adoptedFamily.children.filter((unit) => unit.nodes.some(withIds(sharedIDs, false)));
    setDefaultUnitShift(adoptedFamily);
  };

  // vendor/relatives-tree/lib/middle/create.js
  var createFamilyWithoutParents = (store) => {
    const family = newFamily(store.getNextId(), FamilyType.root, true);
    family.children = createChildUnitsFunc(store)(family.id, store.root);
    setDefaultUnitShift(family);
    return [family];
  };
  var getParentIDs = (root, type) => root.parents.filter(withRelType(type)).map(prop("id"));
  var createDiffTypeFamilies = (store) => {
    const createFamily = createFamilyFunc(store);
    const bloodFamily = createFamily(getParentIDs(store.root, RelType.blood), FamilyType.root, true);
    const adoptedFamily = createFamily(getParentIDs(store.root, RelType.adopted));
    correctOverlaps(bloodFamily, adoptedFamily);
    return [bloodFamily, adoptedFamily];
  };
  var createBloodFamilies = (store) => {
    const createFamily = createFamilyFunc(store);
    const mainFamily = createFamily(store.root.parents.map(prop("id")), FamilyType.root, true);
    const parents2 = unitsToNodes(mainFamily.parents);
    if (parents2.length === NODES_IN_COUPLE) {
      const { left, right } = getSpouseNodesFunc(store)(parents2);
      return [
        left.map((node) => createFamily([node.id])),
        mainFamily,
        right.map((node) => createFamily([node.id]))
      ].flat();
    }
    return [mainFamily];
  };

  // vendor/relatives-tree/lib/middle/index.js
  var arrangeFamilies = (families) => {
    for (let i = 1; i < families.length; i++) {
      families[i].X = rightOf(families[i - 1]);
    }
  };
  var inMiddleDirection = (store) => {
    const families = store.root.parents.length ? hasDiffParents(store.root) ? createDiffTypeFamilies(store) : createBloodFamilies(store) : createFamilyWithoutParents(store);
    arrangeFamilies(families);
    families.forEach((family) => store.families.set(family.id, family));
    return store;
  };

  // vendor/relatives-tree/lib/parents/create.js
  var getParentUnits = (store, unit) => unit.nodes.reduce((units, child) => {
    const parents2 = store.getNodes(child.parents.map(prop("id"))).sort(byGender(store.root.gender));
    if (parents2.length)
      units.push(newUnit(unit.fid, parents2));
    return units;
  }, []);
  var setDefaultUnitShift2 = (family) => {
    arrangeInOrder(family.children);
    arrangeInOrder(family.parents);
  };
  var createFamilyFunc2 = (store) => {
    return (childIDs) => {
      const family = newFamily(store.getNextId(), FamilyType.parent);
      const childUnit = newUnit(family.id, store.getNodes(childIDs), true);
      family.children = [childUnit];
      family.parents = getParentUnits(store, childUnit);
      setDefaultUnitShift2(family);
      return family;
    };
  };

  // vendor/relatives-tree/lib/parents/update.js
  var updateFamilyFunc = (store) => (family, childUnit) => {
    const childFamily = store.getFamily(childUnit.fid);
    family.cid = childFamily.id;
    family.Y = childFamily.Y - SIZE;
    family.X = getUnitX(childFamily, childUnit);
  };

  // vendor/relatives-tree/lib/parents/arrange.js
  var arrangeNextFamily = (family, nextFamily, right) => {
    const unit = family.children[0];
    const index = nextFamily.parents.findIndex(sameAs(unit));
    index === 0 && nextFamily.parents[index].pos === 0 ? nextFamily.X = getUnitX(family, unit) : nextFamily.parents[index].pos = getUnitX(family, unit) - nextFamily.X;
    const nextIdx = nextIndex(index);
    if (nextFamily.parents[nextIdx]) {
      correctUnitsShift(nextFamily.parents.slice(nextIdx), right - getUnitX(nextFamily, nextFamily.parents[nextIdx]));
    }
  };
  var arrangeFamiliesFunc = (store) => (family) => {
    let right = 0;
    while (family.cid) {
      right = Math.max(right, rightOf(family));
      const nextFamily = store.getFamily(family.cid);
      const unit = family.children[0];
      if (!nextFamily.cid) {
        unit.pos = (widthOf(family) - nodeCount(unit) * SIZE) / 2;
      } else {
        if (family.parents.length === 2 && unitNodesCount(family.parents) > 2)
          unit.pos = Math.floor(family.parents[1].pos / 2);
        arrangeNextFamily(family, nextFamily, right);
      }
      family = nextFamily;
    }
  };

  // vendor/relatives-tree/lib/parents/index.js
  var getParentUnitsWithParents = (family) => family.parents.filter((unit) => unit.nodes.some((node) => !!node.parents.length));
  var inParentDirection = (store) => {
    const createFamily = createFamilyFunc2(store);
    const updateFamily = updateFamilyFunc(store);
    const arrangeFamily = arrangeFamiliesFunc(store);
    let stack = getParentUnitsWithParents(store.rootFamily);
    while (stack.length) {
      const childUnit = stack.pop();
      const family = createFamily(nodeIds(childUnit));
      updateFamily(family, childUnit);
      arrangeFamily(family);
      store.families.set(family.id, family);
      stack = stack.concat(getParentUnitsWithParents(family).reverse());
    }
    return store;
  };

  // vendor/relatives-tree/lib/children/update.js
  var updateFamilyFunc2 = (store) => (family, parentUnit) => {
    const parentFamily = store.getFamily(parentUnit.fid);
    family.pid = parentFamily.id;
    family.Y = parentFamily.Y + heightOf(parentFamily) - SIZE;
    family.X = getUnitX(parentFamily, parentUnit);
  };

  // vendor/relatives-tree/lib/utils/arrangeParentsIn.js
  var calcShifts = (units, ids) => units.reduce((acc, unit) => {
    const index = unit.nodes.findIndex(withIds(ids));
    if (index !== -1)
      acc.push(unit.pos + index * SIZE);
    return acc;
  }, []);
  var middle = (values) => {
    const result = (values[0] + values[values.length - 1]) / 2;
    return Number.isNaN(result) ? 0 : result;
  };
  var arrangeParentsIn = (family) => {
    if (!family.parents.length || family.children.length <= 1)
      return;
    family.parents.forEach((unit) => {
      const ids = unit.nodes[0].children.map(prop("id"));
      unit.pos = Math.floor(middle(calcShifts(family.children, ids)) - (unitNodesCount(family.parents) - 1));
    });
  };

  // vendor/relatives-tree/lib/children/arrange.js
  var arrangeNextFamily2 = (family, nextFamily, right) => {
    const unit = family.parents[0];
    const index = nextFamily.children.findIndex(sameAs(unit));
    index === 0 ? nextFamily.X = getUnitX(family, unit) - nextFamily.children[index].pos : nextFamily.children[index].pos = getUnitX(family, unit) - nextFamily.X;
    const nextIdx = nextIndex(index);
    if (nextFamily.children[nextIdx]) {
      correctUnitsShift(nextFamily.children.slice(nextIdx), right - getUnitX(nextFamily, nextFamily.children[nextIdx]));
    }
  };
  var arrangeMiddleFamilies = (families, fid, startFrom) => {
    const start = nextIndex(families.findIndex(withId(fid)));
    const family = families[start];
    if (family) {
      const shift = startFrom - family.X;
      for (let i = start; i < families.length; i++)
        families[i].X += shift;
    }
  };
  var arrangeFamiliesFunc2 = (store) => (family) => {
    let right = 0;
    while (family.pid) {
      right = Math.max(right, rightOf(family));
      const nextFamily = store.getFamily(family.pid);
      arrangeNextFamily2(family, nextFamily, right);
      arrangeParentsIn(nextFamily);
      if (!nextFamily.pid)
        arrangeMiddleFamilies(store.rootFamilies, nextFamily.id, Math.max(right, rightOf(nextFamily)));
      family = nextFamily;
    }
  };

  // vendor/relatives-tree/lib/children/index.js
  var getUnitsWithChildren = (family) => family.children.filter(hasChildren).reverse();
  var inChildDirection = (store) => {
    const createFamily = createFamilyFunc(store);
    const updateFamily = updateFamilyFunc2(store);
    const arrangeFamilies2 = arrangeFamiliesFunc2(store);
    store.familiesArray.filter(withType(FamilyType.root)).forEach((rootFamily) => {
      let stack = getUnitsWithChildren(rootFamily);
      while (stack.length) {
        const parentUnit = stack.pop();
        const family = createFamily(nodeIds(parentUnit), FamilyType.child);
        updateFamily(family, parentUnit);
        arrangeFamilies2(family);
        store.families.set(family.id, family);
        stack = stack.concat(getUnitsWithChildren(family));
      }
    });
    return store;
  };

  // vendor/relatives-tree/lib/connectors/parents.js
  var getChildIDs = (unit) => unit.nodes.map(prop("children")).flat().map(prop("id"));
  var calcConnectors = (family) => (connectors2, unit) => {
    const pX = getParentsX(family, unit);
    const pY = family.Y + HALF_SIZE;
    const mY = family.Y + SIZE;
    if (nodeCount(unit) === NODES_IN_COUPLE)
      connectors2.push([pX - HALF_SIZE, pY, pX + HALF_SIZE, pY]);
    connectors2.push([pX, pY, pX, mY]);
    const child = family.children[0];
    const cX = getUnitX(family, child) + child.nodes.findIndex(withIds(getChildIDs(unit))) * SIZE + HALF_SIZE;
    connectors2.push([cX, mY, cX, mY + HALF_SIZE]);
    if (pX !== cX)
      connectors2.push([Math.min(pX, cX), mY, Math.max(pX, cX), mY]);
    return connectors2;
  };
  var parents = (families) => families.filter(withType(FamilyType.parent)).reduce((connectors2, family) => {
    return connectors2.concat(family.parents.reduce(calcConnectors(family), []));
  }, []);

  // vendor/relatives-tree/lib/connectors/middle.js
  var calcConnectors2 = (family, families) => (connectors2, unit) => {
    const pX = getUnitX(family, unit) + HALF_SIZE;
    const pY = family.Y + HALF_SIZE;
    if (nodeCount(unit) === NODES_IN_COUPLE) {
      connectors2.push([pX, pY, pX + SIZE, pY]);
    } else if (nodeCount(unit) === 1 && unit.nodes[0].spouses.length) {
      families.filter((item) => item.id !== family.id).forEach((other) => {
        other.parents.forEach((parent) => {
          if (parent.nodes.some(withId(unit.nodes[0].spouses[0].id))) {
            const xX = [pX, getUnitX(other, parent) + HALF_SIZE].sort(inAscOrder);
            connectors2.push([xX[0], pY, xX[1], pY]);
          }
        });
      });
    }
    return connectors2;
  };
  var middle2 = (families) => {
    const rootFamilies = families.filter(withType(FamilyType.root));
    return rootFamilies.reduce((connectors2, family) => connectors2.concat(family.parents.reduce(calcConnectors2(family, rootFamilies), [])), []);
  };

  // vendor/relatives-tree/lib/connectors/children.js
  var children = (families) => families.filter(withType(FamilyType.root, FamilyType.child)).reduce((connectors2, family) => {
    const parent = family.parents[0];
    const pX = getParentsX(family, parent);
    const mY = family.Y + (parent ? SIZE : 0);
    if (parent && parent.nodes.every((node) => !!node.children.length)) {
      const pY = family.Y + HALF_SIZE;
      connectors2.push([pX, pY, pX, mY]);
    }
    const parentIds = family.parents.map(nodeIds).flat();
    const positions = [];
    family.children.forEach((unit) => {
      const left = getUnitX(family, unit) + HALF_SIZE;
      unit.nodes.forEach((node, index) => {
        if (node.parents.some(withIds(parentIds))) {
          const nX = left + index * SIZE;
          positions.push(nX);
          connectors2.push([nX, mY, nX, mY + HALF_SIZE]);
        }
      });
      if (nodeCount(unit) === NODES_IN_COUPLE) {
        connectors2.push([left, mY + HALF_SIZE, left + SIZE, mY + HALF_SIZE]);
      } else if (nodeCount(unit) === 1 && unit.nodes[0].spouses.length) {
        family.children.forEach((nUnit) => {
          if (nUnit.nodes.some(withId(unit.nodes[0].spouses[0].id))) {
            const xX = [left, getUnitX(family, nUnit) + HALF_SIZE].sort(inAscOrder);
            connectors2.push([xX[0], mY + HALF_SIZE, xX[1], mY + HALF_SIZE]);
          }
        });
      }
    });
    if (positions.length > 1)
      connectors2.push([min(positions), mY, max(positions), mY]);
    else if (positions.length === 1 && pX !== positions[0])
      connectors2.push([Math.min(pX, positions[0]), mY, Math.max(pX, positions[0]), mY]);
    return connectors2;
  }, []);

  // vendor/relatives-tree/lib/connectors/index.js
  var toConnectors = (families) => (fn) => fn(families);
  var connectors = (families) => [parents, middle2, children].map(toConnectors(families)).flat();

  // vendor/relatives-tree/lib/utils/correctPositions.js
  var alignGenerations = (families, root) => {
    const parents2 = families.find((family) => family.cid === root.id);
    if (parents2) {
      const shift = getUnitX(parents2, parents2.children[0]) - getUnitX(root, root.parents[0]);
      families.filter(withType(FamilyType.child, FamilyType.root)).forEach((family) => family.X += shift);
    }
  };
  var correct = (families, coordinate) => {
    const shift = min(families.map(prop(coordinate))) * -1;
    if (shift !== 0)
      families.forEach((family) => family[coordinate] += shift);
  };
  var correctPositions = (store) => {
    const families = store.familiesArray;
    alignGenerations(families, store.rootFamily);
    correct(families, "Y");
    correct(families, "X");
    return store;
  };

  // vendor/relatives-tree/lib/utils/getCanvasSize.js
  var getCanvasSize = (families) => ({
    width: max(families.map(rightOf)),
    height: max(families.map(bottomOf))
  });

  // vendor/relatives-tree/lib/utils/hasHiddenRelatives.js
  var inUnits = (units, nodeId) => units.some((unit) => unit.nodes.some(withId(nodeId)));
  var hasHiddenRelatives = (family, node) => {
    if (family.type !== FamilyType.child && inUnits(family.parents, node.id)) {
      return family.type === FamilyType.parent && (node.children.length > 1 || node.spouses.length > 1) || !node.parents.length && !!node.siblings.length;
    }
    if (family.type !== FamilyType.parent && inUnits(family.children, node.id)) {
      const parentIds = family.parents.length ? nodeIds(family.parents[0]) : [];
      return !node.parents.some(withIds(parentIds)) && (!!node.parents.length || !!node.siblings.length);
    }
    return false;
  };

  // vendor/relatives-tree/lib/utils/getExtendedNodes.js
  var extendNode = (family) => (unit) => unit.nodes.map((node, idx) => Object.assign(Object.assign({}, node), { top: family.Y + (unit.child && !!family.parents.length ? SIZE : 0), left: getUnitX(family, unit) + idx * SIZE, hasSubTree: hasHiddenRelatives(family, node) }));
  var getParentNodes = (family) => ([FamilyType.root, FamilyType.parent].includes(family.type) ? family.parents : []).map(extendNode(family));
  var getChildNodes = (family) => ([FamilyType.root, FamilyType.child].includes(family.type) ? family.children : []).map(extendNode(family));
  var mapFamily = (family) => [getParentNodes(family), getChildNodes(family)].flat(2);
  var getExtendedNodes = (families) => families.map(mapFamily).flat();

  // vendor/relatives-tree/lib/index.js
  var calcFamilies = pipe(inMiddleDirection, inParentDirection, inChildDirection, correctPositions);
  var lib_default = (nodes, options) => {
    const store = new store_default(nodes, options.rootId);
    if (options.placeholders)
      placeholders(store);
    const families = calcFamilies(store).familiesArray;
    return {
      families,
      canvas: getCanvasSize(families),
      nodes: getExtendedNodes(families),
      connectors: connectors(families)
    };
  };

  // app/genealogy-tree/entry.js
  globalThis.createTree = (familyData, options) => lib_default(familyData, { rootId: options.rootId, placeholders: options.placeholders });
  globalThis.calcRelativesTree = globalThis.createTree;
})();
