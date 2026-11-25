package com.autobot.shopeeblock

data class MoveStep(
    val piece: PieceShape,
    val row: Int,
    val col: Int,
    val clearedLines: Int
)

class BlockSolver {

    private val rowMasks = LongArray(8) { r -> 0xFFL shl (r * 8) }
    private val colMasks = LongArray(8) { c ->
        var m = 0L
        for (r in 0 until 8) m = m or (1L shl (r * 8 + c))
        m
    }

    private data class CandidateState(
        val board: Long,
        val steps: List<MoveStep>,
        val totalCleared: Int,
        val score: Double
    )

    fun solveBestSequence(initialBoard: Long, pieces: List<PieceShape>): List<MoveStep>? {
        if (pieces.isEmpty()) return null

        val permutations = generatePermutations(pieces)
        var bestOverall: CandidateState? = null

        for (perm in permutations) {
            var beam = listOf(CandidateState(initialBoard, emptyList(), 0, 0.0))

            for (piece in perm) {
                val nextStates = ArrayList<CandidateState>(256)
                val maxR = 8 - piece.rows
                val maxC = 8 - piece.cols

                var baseMask = 0L
                for ((pr, pc) in piece.cells) {
                    baseMask = baseMask or (1L shl (pr * 8 + pc))
                }

                for (state in beam) {
                    for (r in 0..maxR) {
                        val rowShift = r * 8
                        for (c in 0..maxC) {
                            val placedMask = baseMask shl (rowShift + c)
                            if ((state.board and placedMask) == 0L) {
                                val combined = state.board or placedMask
                                val (clearedBoard, linesCleared) = clearFullLines(combined)
                                val newTotalCleared = state.totalCleared + linesCleared
                                val heuristic = evaluateBoard(clearedBoard, newTotalCleared, linesCleared)
                                nextStates.add(
                                    CandidateState(
                                        board = clearedBoard,
                                        steps = state.steps + MoveStep(piece, r, c, linesCleared),
                                        totalCleared = newTotalCleared,
                                        score = heuristic
                                    )
                                )
                            }
                        }
                    }
                }

                if (nextStates.isEmpty()) {
                    beam = emptyList()
                    break
                }

                val bestByBoard = HashMap<Long, CandidateState>(nextStates.size)
                for (st in nextStates) {
                    val existing = bestByBoard[st.board]
                    if (existing == null || st.score > existing.score) {
                        bestByBoard[st.board] = st
                    }
                }

                beam = bestByBoard.values
                    .sortedByDescending { it.score }
                    .take(36)
            }

            if (beam.isNotEmpty()) {
                val top = beam.first()
                if (bestOverall == null || top.score > bestOverall.score) {
                    bestOverall = top
                }
            }
        }

        if (bestOverall == null && pieces.size > 1) {
            return solvePartialFallback(initialBoard, pieces)
        }

        return bestOverall?.steps
    }

    private fun solvePartialFallback(board: Long, pieces: List<PieceShape>): List<MoveStep>? {
        for (i in pieces.indices) {
            val sub = pieces.filterIndexed { idx, _ -> idx != i }
            val res = solveBestSequence(board, sub)
            if (!res.isNullOrEmpty()) return res
        }
        return null
    }

    private fun clearFullLines(board: Long): Pair<Long, Int> {
        var clearMask = 0L
        var lines = 0
        for (r in 0 until 8) {
            if ((board and rowMasks[r]) == rowMasks[r]) {
                clearMask = clearMask or rowMasks[r]
                lines++
            }
        }
        for (c in 0 until 8) {
            if ((board and colMasks[c]) == colMasks[c]) {
                clearMask = clearMask or colMasks[c]
                lines++
            }
        }
        return Pair(board and clearMask.inv(), lines)
    }

    private fun evaluateBoard(board: Long, totalCleared: Int, stepCleared: Int): Double {
        val filledCells = java.lang.Long.bitCount(board)
        val emptyCells = 64 - filledCells

        var transitions = 0
        for (r in 0 until 8) {
            var prev = 1
            for (c in 0 until 8) {
                val cur = ((board ushr (r * 8 + c)) and 1L).toInt()
                if (cur != prev) transitions++
                prev = cur
            }
            if (prev == 0) transitions++
        }
        for (c in 0 until 8) {
            var prev = 1
            for (r in 0 until 8) {
                val cur = ((board ushr (r * 8 + c)) and 1L).toInt()
                if (cur != prev) transitions++
                prev = cur
            }
            if (prev == 0) transitions++
        }

        var isolatedHoles = 0
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                if (((board ushr (r * 8 + c)) and 1L) == 0L) {
                    val up = if (r == 0) 1L else ((board ushr ((r - 1) * 8 + c)) and 1L)
                    val dn = if (r == 7) 1L else ((board ushr ((r + 1) * 8 + c)) and 1L)
                    val lt = if (c == 0) 1L else ((board ushr (r * 8 + c - 1)) and 1L)
                    val rt = if (c == 7) 1L else ((board ushr (r * 8 + c + 1)) and 1L)
                    if (up == 1L && dn == 1L && lt == 1L && rt == 1L) {
                        isolatedHoles++
                    }
                }
            }
        }

        var open3x3 = 0
        val mask3x3 = (0b111L) or (0b111L shl 8) or (0b111L shl 16)
        for (r in 0..5) {
            for (c in 0..5) {
                if ((board and (mask3x3 shl (r * 8 + c))) == 0L) {
                    open3x3++
                }
            }
        }

        val clearBonus = totalCleared * 28.0 + if (stepCleared >= 2) stepCleared * 18.0 else 0.0

        return (emptyCells * 3.8) +
            clearBonus +
            (open3x3 * 9.5) -
            (transitions * 2.4) -
            (isolatedHoles * 22.0)
    }

    private fun <T> generatePermutations(list: List<T>): List<List<T>> {
        if (list.size <= 1) return listOf(list)
        val result = mutableListOf<List<T>>()
        for (i in list.indices) {
            val elem = list[i]
            val rest = list.filterIndexed { idx, _ -> idx != i }
            for (perm in generatePermutations(rest)) {
                result.add(listOf(elem) + perm)
            }
        }
        return result
    }
}
