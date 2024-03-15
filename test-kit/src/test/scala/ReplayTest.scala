package chess

import cats.syntax.option.*
import format.{ EpdFen, Fen, Uci }
import chess.format.pgn.SanStr
import chess.variant.Chess960
import chess.format.pgn.Fixtures

class ReplayTest extends ChessTest:

  test("replay from position close chess"):
    val fen   = EpdFen("""8/rnbqkbnr/pppppppp/8/8/PPPPPPPP/RNBQKBNR/8 w - - 0 1""")
    val moves = SanStr from """d4 d5 Nf4 Nf5 g4 g5 gxf5 exf5""".split(' ').toList
    assertMatch(Replay.gameMoveWhileValid(moves, fen, variant.FromPosition)):
      case (_, games, None) =>
        assertEquals(games.size, 8)
        assertEquals(games(1)._2._2, "d5")

  test("replay errors should keep order"):
    val fen   = EpdFen("""8/rnbqkbnr/pppppppp/8/8/PPPPPPPP/RNBQKBNR/8 w - - 0 1""")
    val moves = SanStr from """d4 d5 Nf3""".split(' ').toList
    assertMatch(Replay.gameMoveWhileValid(moves, fen, variant.FromPosition)):
      case (_, games, Some(_)) =>
        assertEquals(games.size, 2)
        assertEquals(games(1)._2._2, "d5")

  test("bongcloud attack"):
    Replay
      .situationsFromUci(
        moves = List(Uci("e2e4"), Uci("e7e5"), Uci("e1e2")).flatten,
        initialFen = None,
        variant = variant.Standard
      )
      .assertRight: situations =>
        assertEquals(
          situations.map(Fen.write),
          List(
            EpdFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
            EpdFen("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"),
            EpdFen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 1"),
            EpdFen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPPKPPP/RNBQ1BNR b kq - 1 1")
          )
        )

  test("racing kings"):
    assert:
      Replay
        .situations(
          sans = SanStr.from("Be3 Ne4 Rg3 Nxe3 Rxe3" `split` " "),
          initialFen = None,
          variant = chess.variant.RacingKings
        )
        .isRight

  test("chess960 castlings"):
    val sans: Vector[SanStr] =
      SanStr from "e4 e5 Ne3 f6 Nb3 Nb6 O-O-O Ne6 Ba6 O-O-O Nd5 Nxd5 Bxb7+ Kb8"
        .split(' ')
        .toVector
    val (game, steps, error) = chess.Replay.gameMoveWhileValid(
      sans,
      Fen.Epd("nrknbbqr/pppppppp/8/8/8/8/PPPPPPPP/NRKNBBQR w KQkq - 0 1"),
      Chess960
    )
    assertEquals(error, None)
    assertEquals(steps.size, sans.size)

  test("castling always 960 notation"):
    val sans: Vector[SanStr] =
      SanStr from "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 f3 O-O Be3 e5 d5 Nh5 Qd2 Qh4+ g3 Qe7 O-O-O"
        .split(' ')
        .toVector
    val (game, steps, error) = chess.Replay.gameMoveWhileValid(
      sans,
      Fen.Epd.initial,
      variant.Standard
    )
    assertEquals(error, None)
    assertEquals(steps.size, sans.size)
    assertEquals(steps(9)._2.uci.uci, "e8h8")
    assertEquals(steps(18)._2.uci.uci, "e1a1")

  test("replay from fen then castle"):
    val fen                  = Fen.Epd("2bqkb1r/1pp1ppp1/7r/pN2p2p/3PP3/P3P3/1PP1B1PP/R2Q1RK1 w k - 3 13")
    val moves                = SanStr from "dxe5 Qxd1 Raxd1 Rc6 Rd2 e6 Rfd1 Be7 Na7 O-O".split(" ")
    val (game, steps, error) = chess.Replay.gameMoveWhileValid(moves, fen, variant.Standard)
    assertEquals(error, None)
    assertEquals(steps.size, moves.size)

  test("antichess replay invalid san"):
    val fen   = EpdFen("r1b1k3/pp3p2/n2pp3/2p5/4p3/2P2N2/P4n2/R7 b - - 0 17")
    val moves = List(SanStr("c4"))
    assertMatch(Replay.gameMoveWhileValid(moves, fen, variant.Antichess)):
      case (_, games, Some(_)) => assertEquals(games.size, 0)

  test("Castling should not be possible when in check"):
    val fen   = EpdFen("r1bq1rk1/ppp2ppp/2n5/8/1bB1Pp2/5N2/PPP1Q1PP/R1B1K2R w KQ - 1 10")
    val moves = List(SanStr("O-O"))
    assertMatch(Replay.gameMoveWhileValid(moves, fen, variant.Standard)):
      case (_, games, Some(_)) => assertEquals(games.size, 0)

  test("Castling in Antichess is not allowed"):
    val fen   = EpdFen("r3kbnr/p3pp1p/1p4p1/8/8/P7/1P1P1PPP/RNB2KNR b - - 0 9")
    val moves = List(SanStr("O-O-0"))
    assertMatch(Replay.gameMoveWhileValid(moves, fen, variant.Antichess)):
      case (_, games, Some(_)) => assertEquals(games.size, 0)

  test("replay from standard positions"):
    val nb    = 500
    val games = Fixtures.prod500standard
    (games take nb)
      .map: g =>
        SanStr from g.split(' ').toList
      .foreach: moves =>
        assertMatch(Replay.gameMoveWhileValid(moves, chess.format.Fen.initial, chess.variant.Standard)):
          case (_, games, None) => assertEquals(games.size, moves.size)
