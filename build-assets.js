const fs = require('fs');
const path = require('path');
const source = path.join(__dirname, '..', 'node_modules', 'jsmediatags', 'dist', 'jsmediatags.min.js');
const target = path.join(__dirname, '..', 'www', 'jsmediatags.min.js');
if (!fs.existsSync(source)) throw new Error('No se encontró jsmediatags. Ejecuta npm install.');
fs.copyFileSync(source, target);
console.log('Metadata reader copied to www/jsmediatags.min.js');
