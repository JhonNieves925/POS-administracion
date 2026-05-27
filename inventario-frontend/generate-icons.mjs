import { writeFileSync, mkdirSync } from 'fs'
import { deflateSync } from 'zlib'

// CRC32
const crcTable = new Uint32Array(256)
for (let n = 0; n < 256; n++) {
  let c = n
  for (let k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320 ^ (c >>> 1) : c >>> 1
  crcTable[n] = c
}
function crc32(buf) {
  let crc = 0xFFFFFFFF
  for (let i = 0; i < buf.length; i++) crc = crcTable[(crc ^ buf[i]) & 0xFF] ^ (crc >>> 8)
  return (crc ^ 0xFFFFFFFF) >>> 0
}
function pngChunk(type, data) {
  const lenBuf = Buffer.alloc(4); lenBuf.writeUInt32BE(data.length)
  const typeBuf = Buffer.from(type)
  const body = Buffer.concat([typeBuf, data])
  const crcBuf = Buffer.alloc(4); crcBuf.writeUInt32BE(crc32(body))
  return Buffer.concat([lenBuf, body, crcBuf])
}

function makePNG(size) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8   // bit depth
  ihdr[9] = 2   // color type RGB
  ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0

  const bgR = 211, bgG = 47, bgB = 47   // #D32F2F rojo DISTRIASOCIADOS
  const fgR = 255, fgG = 255, fgB = 255 // blanco

  const cx = size / 2, cy = size / 2
  const outerR = size * 0.45
  const innerR = size * 0.28
  // letra "D" simplificada: rectángulo + semicírculo derecho
  const rows = []
  for (let y = 0; y < size; y++) {
    const row = Buffer.alloc(1 + size * 3)
    row[0] = 0 // filter None
    for (let x = 0; x < size; x++) {
      const dx = x - cx, dy = y - cy
      const dist = Math.sqrt(dx * dx + dy * dy)

      // fondo: círculo rojo
      let R = bgR, G = bgG, B = bgB
      if (dist <= outerR) {
        // interior blanco por defecto
        R = fgR; G = fgG; B = fgB

        // dibujar letra D (barra vertical izquierda + arco derecho)
        const lx = cx - outerR * 0.35
        const barW = outerR * 0.18
        const barH = outerR * 0.7
        const inBar = x >= lx - barW && x <= lx + barW && Math.abs(dy) <= barH

        // arco derecho: anillo entre innerR y outerR*0.85, solo lado derecho
        const arcOuter = outerR * 0.72
        const arcInner = outerR * 0.42
        const arcDx = x - (lx + barW)
        const arcDist = Math.sqrt(arcDx * arcDx + dy * dy)
        const inArc = arcDist >= arcInner && arcDist <= arcOuter && arcDx >= 0 && Math.abs(dy) <= barH

        if (inBar || inArc) {
          R = bgR; G = bgG; B = bgB // rojo (la letra)
        }
      }

      row[1 + x * 3] = R
      row[2 + x * 3] = G
      row[3 + x * 3] = B
    }
    rows.push(row)
  }

  const rawData = Buffer.concat(rows)
  const compressed = deflateSync(rawData, { level: 6 })
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])

  return Buffer.concat([
    sig,
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', compressed),
    pngChunk('IEND', Buffer.alloc(0))
  ])
}

mkdirSync('public/icons', { recursive: true })
writeFileSync('public/icons/icon-192.png', makePNG(192))
writeFileSync('public/icons/icon-512.png', makePNG(512))
console.log('✓ Iconos generados en public/icons/')
