import calcTree from '../../vendor/relatives-tree/lib/index.js';

/** API alias from TZ: createTree → calcTree (SanichKotikov/relatives-tree, MIT). */
globalThis.createTree = (familyData, options) =>
  calcTree(familyData, { rootId: options.rootId, placeholders: options.placeholders });

globalThis.calcRelativesTree = globalThis.createTree;
