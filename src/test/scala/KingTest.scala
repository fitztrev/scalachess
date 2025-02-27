package chess

import scala.language.implicitConversions
import Square.*

class KingTest extends ChessTest:

  "a king" should:

    val king = White - King

    "move 1 position in any direction" in:
      pieceMoves(king, D4) must bePoss(D3, C3, C4, C5, D5, E5, E4, E3)

    "move 1 position in any direction, even from the edges" in:
      pieceMoves(king, H8) must bePoss(H7, G7, G8)

    "move behind pawn barrier" in:
      """
PPPPPPPP
R  QK NR""" destsFrom E1 must bePoss(F1)

    "not move to positions that are occupied by the same colour" in:
      val board = """
   P
NPKP   P

PPPPPPPP
 NBQQBNN
"""
      board destsFrom C4 must bePoss(
        board,
        """



 xxP
NPKP   P
 xxx
PPPPPPPP
 NBQQBNN
"""
      )

    "capture hanging opponent pieces" in:
      val board = """
 bpp   k
  Kp
 p

"""
      board destsFrom C3 must bePoss(
        board,
        """




 xxx   k
  Kp
 x

"""
      )
    "not move near from the other king" in:
      """
   k
 K
""" destsFrom B1 must bePoss(A1, A2, B2)
