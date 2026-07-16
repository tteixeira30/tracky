// Gera os ícones PNG da PWA a partir do logo SVG (public/logo.svg).
//
// Uso:  node scripts/generate-icons.mjs   (a partir da pasta frontend/)
//
// Produz em public/:
//   pwa-192x192.png            — ícone standard 192x192
//   pwa-512x512.png            — ícone standard 512x512
//   pwa-maskable-512x512.png   — ícone maskable (logo com ~20% de padding
//                                sobre fundo da cor de tema, para Android
//                                poder recortar em círculo/squircle)
//   apple-touch-icon.png       — 180x180 para iOS (ecrã inicial)
//
// Requer o devDependency `sharp` (npm install -D sharp).

import sharp from 'sharp'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const publicDir = path.join(__dirname, '..', 'public')
const logoPath = path.join(publicDir, 'logo.svg')

// Cor de fundo do tema escuro — deve corresponder à var --bg em src/styles.css (:root).
const THEME_BG = '#0b0d13'

const svg = await readFile(logoPath)

// Ícones standard: o logo ocupa todo o canvas (o próprio SVG já tem cantos arredondados).
async function standard(size, out) {
  await sharp(svg, { density: 300 })
    .resize(size, size)
    .png()
    .toFile(path.join(publicDir, out))
  console.log(`✓ ${out} (${size}x${size})`)
}

// Ícone maskable: logo reduzido com ~20% de padding de cada lado,
// centrado sobre um fundo opaco da cor de tema (zona segura do recorte Android).
async function maskable(size, out) {
  const pad = Math.round(size * 0.2)
  const inner = size - pad * 2
  const logo = await sharp(svg, { density: 300 }).resize(inner, inner).png().toBuffer()
  await sharp({
    create: { width: size, height: size, channels: 4, background: THEME_BG },
  })
    .composite([{ input: logo, left: pad, top: pad }])
    .png()
    .toFile(path.join(publicDir, out))
  console.log(`✓ ${out} (${size}x${size}, maskable)`)
}

await standard(192, 'pwa-192x192.png')
await standard(512, 'pwa-512x512.png')
await standard(180, 'apple-touch-icon.png')
await maskable(512, 'pwa-maskable-512x512.png')

console.log('Ícones gerados em', publicDir)
