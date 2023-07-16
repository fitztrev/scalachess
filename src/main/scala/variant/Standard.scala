package chess
package variant

import bitboard.Bitboard
import bitboard.Bitboard.*

case object Standard
    extends Variant(
      id = Variant.Id(1),
      key = Variant.LilaKey("standard"),
      uciKey = Variant.UciKey("chess"),
      name = "Standard",
      shortName = "Std",
      title = "Standard rules of chess (FIDE)",
      standardInitialPosition = true
    ):

  val pieces: Map[Square, Piece] = Variant.symmetricRank(backRank)

  def validMoves(situation: Situation): List[Move] =
    import situation.{ genNonKing, genSafeKing, genCastling, color, board }
    val enPassantMoves = situation.genEnPassant(situation.us & board.pawns)
    board
      .kingPosOf(color)
      .fold(Nil)(king =>
        val checkers = board.attackers(king, !situation.color)
        val candidates =
          if checkers.isEmpty then
            val targets = ~situation.us
            genNonKing(targets) ::: genSafeKing(targets) ::: genCastling(king) ::: enPassantMoves
          else genEvasions(situation, checkers) ::: enPassantMoves
        val sliderBlockers = board.sliderBlockers(king, color)
        if sliderBlockers.nonEmpty || enPassantMoves.nonEmpty then
          candidates.filter(isSafe(situation, king, _, sliderBlockers))
        else candidates
      )

  // Used for filtering candidate moves that would leave put the king in check.
  def isSafe(situation: Situation, king: Square, move: Move, blockers: Bitboard): Boolean =
    import situation.{ board, us, them }
    if move.enpassant then
      val newOccupied = (board.occupied ^ move.orig.bb ^ move.dest.withRankOf(move.orig).bb) | move.dest.bb
      (king.rookAttacks(newOccupied) & them & (board.rooks ^ board.queens)).isEmpty &&
      (king.bishopAttacks(newOccupied) & them & (board.bishops ^ board.queens)).isEmpty
    else if !move.castles || !move.promotes then
      !(us & blockers).contains(move.orig) || Bitboard.aligned(move.orig, move.dest, king)
    else true

  private def genEvasions(situation: Situation, checkers: Bitboard): List[Move] =
    import situation.{ genNonKing, genSafeKing, us, board, ourKing }
    ourKing.fold(Nil)(king =>
      // Checks by these sliding pieces can maybe be blocked.
      val sliders = checkers & (board.sliders)
      val attacked =
        sliders.fold(Bitboard.empty)((a, s) => a | (s.bb ^ Bitboard.ray(king, s)))
      val safeKings = genSafeKing(~us & ~attacked)
      val blockers =
        checkers.singleSquare.map(c => genNonKing(Bitboard.between(king, c) | checkers)).getOrElse(Nil)
      safeKings ++ blockers
    )

  override def valid(situation: Situation, strict: Boolean): Boolean =
    super.valid(situation, strict) && (!strict || hasValidCheckers(situation))

  private def hasValidCheckers(situation: Situation): Boolean =
    situation.checkers.fold(true) { checkers_ =>
      situation.potentialEpSquare.fold(isValidChecksForMultipleCheckers(situation, checkers_)) {
        enPassantSquare_ =>
          isValidCheckersForEnPassant(
            enPassantSquare_,
            situation,
            checkers_
          )
      }
    }

  private def isValidCheckersForEnPassant(
      enPassantSquare: Square,
      situation: Situation,
      activeCheckers: Bitboard
  ): Boolean =
    val enPassantUp: Option[Square] = (!situation.color)
      .fold(enPassantSquare.up, enPassantSquare.down)
    val enPassantDown: Option[Square] = (!situation.color)
      .fold(enPassantSquare.down, enPassantSquare.up)

    enPassantUp.exists { enPassantUp_ =>
      enPassantDown.exists { enPassantDown_ =>
        activeCheckers.count == 1 && (
          activeCheckers.first.contains(enPassantSquare) || situation.board
            .move(enPassantUp_, enPassantDown_)
            .exists(previousBoard =>
              situation.ourKing.exists(previousBoard.attackers(_, !situation.color).isEmpty)
            )
        )
      }
    }

  private def isValidChecksForMultipleCheckers(situation: Situation, activeCheckers: Bitboard): Boolean =
    activeCheckers.count <= 1 || (activeCheckers.count == 2 && {
      activeCheckers.first.exists { firstChecker =>
        activeCheckers.squares.lastOption.exists { lastChecker =>
          situation.ourKing.exists { ourKing_ =>
            !Bitboard.aligned(firstChecker, lastChecker, ourKing_)
          }
        }
      }
    })
