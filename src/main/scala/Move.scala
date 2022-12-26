package chess

import chess.format.Uci
import bitboard.Bitboard.*
import cats.syntax.option.*
import cats.kernel.Monoid
import chess.bitboard.Bitboard

case class Move(
    piece: Piece,
    orig: Pos,
    dest: Pos,
    situationBefore: Situation,
    after: Board,
    capture: Option[Pos],
    promotion: Option[PromotableRole],
    castle: Option[((Pos, Pos), (Pos, Pos))],
    enpassant: Boolean,
    metrics: MoveMetrics = MoveMetrics()
):
  inline def before = situationBefore.board

  inline def situationAfter = Situation(finalizeAfter, !piece.color)

  inline def withHistory(inline h: History) = copy(after = after withHistory h)

  val isWhiteTurn: Boolean = piece.color.white

  def finalizeAfter: Board =
    val board = after.variant.finalizeBoard(
      after updateHistory { h1 =>
        val h2 = h1.copy(
          lastMove = Option(toUci),
          unmovedRooks = before.unmovedRooks,
          halfMoveClock =
            if (piece.is(Pawn) || captures || promotes) HalfMoveClock(0)
            else h1.halfMoveClock + 1
        )

        val halfCastlingRights: Bitboard =
          if captures then h1.castles & ~dest.bitboard
          else h1.castles

        val castleRights: Bitboard =
          if piece is Rook then halfCastlingRights & ~orig.bitboard
          else if piece.is(King) then halfCastlingRights & Bitboard.rank(piece.color.lastRank)
          else halfCastlingRights

        val epSquare: Option[Pos] =
          if piece is Pawn then
            if Math.abs((orig - dest).value) == 16 then
              // TODO calculate their pawns attacks
              Some(Pos(orig.value + (if isWhiteTurn then 8 else -8)))
            else None
          else None

        h2.withCastles(Castles(castleRights)).copy(epSquare = epSquare)
      },
      toUci,
      capture flatMap { before(_) }
    )

    // Update position hashes last, only after updating the board,
    // castling rights and en-passant rights.
    board updateHistory { h =>
      lazy val positionHashesOfSituationBefore =
        if (h.positionHashes.value.isEmpty) Hash(situationBefore) else h.positionHashes
      val resetsPositionHashes = board.variant.isIrreversible(this)
      val basePositionHashes =
        if (resetsPositionHashes) Monoid[PositionHash].empty else positionHashesOfSituationBefore
      h.copy(positionHashes =
        Monoid[PositionHash].combine(Hash(Situation(board, !piece.color)), basePositionHashes)
      )
    }

  def applyVariantEffect: Move = before.variant addVariantEffect this

  // does this move capture an opponent piece?
  inline def captures = capture.isDefined

  inline def promotes = promotion.isDefined

  inline def castles = castle.isDefined

  inline def normalizeCastle =
    castle.fold(this) { case (_, (rookOrig, _)) =>
      copy(dest = rookOrig)
    }

  inline def color = piece.color

  def withPromotion(op: Option[PromotableRole]): Option[Move] =
    op.fold(this.some) { p =>
      if ((after count color.queen) > (before count color.queen)) for {
        b2 <- after take dest
        b3 <- b2.place(color - p, dest)
      } yield copy(after = b3, promotion = Option(p))
      else this.some
    }

  inline def withAfter(newBoard: Board) = copy(after = newBoard)

  inline def withMetrics(m: MoveMetrics) = copy(metrics = m)

  inline def toUci = Uci.Move(orig, dest, promotion)

  override def toString = s"$piece ${toUci.uci}"
