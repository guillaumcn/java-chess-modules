package com.xchess;

import com.xchess.config.StockfishConfig;
import com.xchess.option.StockfishOptions;
import com.xchess.process.ProcessWrapper;
import com.xchess.validators.FenSyntaxValidator;
import com.xchess.validators.MoveValidator;
import com.xchess.validators.SquareValidator;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class Stockfish {
    private final ProcessWrapper process;
    private final StockfishConfig config;
    private StockfishOptions options;


    public Stockfish(ProcessWrapper process, StockfishConfig config) {
        this.process = process;
        this.config = config;
        this.options = new StockfishOptions();
    }

    public void start() throws IOException, InterruptedException {
        this.process.start();
        this.waitUntilReady();
    }

    public void stop() throws IOException {
        this.process.stop();
    }

    public void setOptions(StockfishOptions options) throws IOException,
            InterruptedException, CloneNotSupportedException {
        this.options = this.options.merge(options);
        List<String> commands = this.options.toCommands();
        for (String command :
                commands) {
            this.process.writeCommand(command);
            this.waitUntilReady();
        }
    }

    public void setDefaultOptions() throws IOException, InterruptedException,
            CloneNotSupportedException {
        this.setOptions(new StockfishOptions().setDefaultOptions());
    }

    public String getFenPosition() throws IOException, InterruptedException {
        this.process.writeCommand("d");
        List<String> lines = this.process.readLinesUntil(Pattern.compile(
                        "^Checkers.+?$"),
                this.config.getReadTimeoutInMs());
        Optional<String> fenLineOptional =
                lines.stream().filter((line) -> line.startsWith("Fen")).findFirst();

        if (fenLineOptional.isEmpty()) {
            throw new IOException("Cannot find line containing fen position");
        }
        return fenLineOptional.get().substring(5);
    }

    public List<String> getPossibleMoves() throws IOException,
            InterruptedException {
        this.process.writeCommand("go perft 1");
        List<String> lines = this.process.readLinesUntil(Pattern.compile(
                        "^Nodes searched.+?$"),
                this.config.getReadTimeoutInMs());
        return lines.stream().filter((line) -> Pattern.matches("^....: 1$",
                line)).map((line) -> line.split(":")[0]).toList();
    }

    public List<String> getPossibleMoves(String square) throws IOException,
            InterruptedException {
        if (SquareValidator.isSquareSyntaxValid(square)) {
            throw new IllegalArgumentException("Invalid syntax for square " + square);
        }
        return this.getPossibleMoves().stream().filter((move) -> move.startsWith(square)).toList();
    }

    public boolean isMovePossible(String move) throws IOException,
            InterruptedException {
        if (MoveValidator.isMoveValid(move)) {
            throw new IllegalArgumentException("Invalid syntax for move " + move);
        }
        return getPossibleMoves().contains(move);
    }

    public boolean isValidFenPosition(String fen) throws IOException,
            InterruptedException {
        if (!FenSyntaxValidator.isFenSyntaxValid(fen)) {
            return false;
        }
        AtomicBoolean isValid = new AtomicBoolean(false);

        Thread t = new Thread(() -> {
            Stockfish tempStockfish =
                    new Stockfish(new ProcessWrapper(this.process.getCommand()),
                            this.config);
            try {
                tempStockfish.start();
                tempStockfish.moveToFenPosition(fen);
                String bestMove = tempStockfish.findBestMove();
                isValid.set(!Objects.isNull(bestMove));
                tempStockfish.stop();
            } catch (Exception e) {
                isValid.set(false);
                try {
                    tempStockfish.stop();
                } catch (IOException ex) {
                    throw new RuntimeException(e);
                }
                throw new RuntimeException(e);
            }
        });
        t.join();

        return isValid.get();
    }

    public void move(List<String> moves) throws IOException,
            InterruptedException {
        String startingPosition = this.getFenPosition();
        int invalidMoveIndex =
                moves.stream().map(MoveValidator::isMoveValid).toList().indexOf(false);
        if (invalidMoveIndex != -1) {
            throw new IllegalArgumentException("Invalid syntax for move " + moves.get(invalidMoveIndex));
        }
        for (String move :
                moves) {
            if (this.isMovePossible(move)) {
                this.process.writeCommand("position fen " + this.getFenPosition() + " moves " + move);
                this.waitUntilReady();
            } else {
                this.moveToFenPosition(startingPosition);
                throw new IllegalArgumentException("Illegal move " + move +
                        " from position " + this.getFenPosition());
            }
        }
    }

    public String moveToStartPosition() throws IOException,
            InterruptedException {
        this.process.writeCommand("position startpos");
        this.waitUntilReady();
        return this.getFenPosition();
    }

    public void moveToFenPosition(String fen) throws IOException,
            InterruptedException {
        this.process.writeCommand("position fen " + fen);
        this.waitUntilReady();
    }

    public String findBestMove() throws IOException, InterruptedException {
        this.process.writeCommand("go depth 10");
        String bestMove = this.getBestMoveFromOutput();
        this.waitUntilReady();

        return bestMove;
    }

    private String getBestMoveFromOutput() throws InterruptedException,
            IOException {
        Optional<String> bestmoveLine =
                this.process.readLinesUntil(Pattern.compile("^bestmove.+?$"),
                        this.config.getReadTimeoutInMs()).stream().filter((line) -> line.startsWith("bestmove")).findFirst();

        if (bestmoveLine.isEmpty()) {
            throw new IOException("Cannot find best move line from output");
        }
        String bestMove = bestmoveLine.get().split(" ")[1];
        return bestMove.equals("(none)") ? null : bestMove;
    }

    public boolean healthCheck() {
        try {
            this.waitUntilReady();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void waitUntilReady() throws IOException, InterruptedException {
        this.process.writeCommand("isready");
        this.process.readLinesUntil("readyok",
                this.config.getReadTimeoutInMs());
    }
}
