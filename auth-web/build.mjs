import { build } from 'esbuild';
import { rm, mkdir, cp } from 'node:fs/promises';
const preview = process.argv.includes('--preview');
const output = preview ? 'preview-dist' : 'dist';
await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });
await cp('public', output, { recursive: true });
await build({ entryPoints: ['src/main.mjs'], outfile: `${output}/app.js`, define: { __LOCAL_PREVIEW__: String(preview) }, bundle: true,
  minify: true, format: 'esm', target: ['es2022'], sourcemap: false });
