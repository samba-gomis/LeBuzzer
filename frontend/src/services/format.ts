/** 1 -> "1er", 2 -> "2e"... */
export function formatRank(rank: number): string {
  return rank === 1 ? '1er' : `${rank}e`
}

/** 1500 -> "1 500" */
export function formatPoints(points: number): string {
  return points.toLocaleString('fr-FR')
}
