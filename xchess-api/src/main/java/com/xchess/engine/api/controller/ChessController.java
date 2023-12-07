package com.xchess.engine.api.controller;

import com.xchess.ChessEngine;
import com.xchess.engine.api.pool.PoolWrapper;
import com.xchess.evaluation.ChessEngineEvaluation;
import com.xchess.evaluation.parameter.EvaluationParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

@RestController
public class ChessController {

    private final PoolWrapper poolWrapper;

    @Autowired
    public ChessController(PoolWrapper poolWrapper) {
        this.poolWrapper = poolWrapper;
    }

    @GetMapping(value = "/getEngineVersion")
    public Float getEngineVersion() throws Exception {
        return this.poolWrapper.queueAction(ChessEngine::getEngineVersion);
    }

    @GetMapping(value = "/getPossibleMoves")
    public List<String> getPossibleMoves(@RequestParam(required = false) String fen,
                                         @RequestParam(required = false) String square) throws Exception {
        return this.poolWrapper.queueAction(engineWorker -> {
            try {
                moveToFenPositionIfDefined(engineWorker, fen);
                if (Objects.isNull(square)) {
                    return engineWorker.getPossibleMoves();
                } else {
                    return engineWorker.getPossibleMoves(square);
                }
            } catch (IOException | TimeoutException e) {
                throw new RuntimeException(e);
            }

        });
    }

    @GetMapping(value = "/findBestMove")
    public String findBestMove(@RequestParam(required = false) String fen) throws Exception {
        return this.poolWrapper.queueAction(engineWorker -> {
            try {
                moveToFenPositionIfDefined(engineWorker, fen);
                EvaluationParameters evaluationParameters =
                        EvaluationParameters.builder()
                                .depth(10)
                                .build();
                return engineWorker.findBestMove(evaluationParameters);
            } catch (IOException | TimeoutException e) {
                throw new RuntimeException(e);
            }

        });
    }

    @GetMapping(value = "/getPositionEvaluation")
    public ChessEngineEvaluation getPositionEvaluation(@RequestParam(required = false) String fen) throws Exception {
        return this.poolWrapper.queueAction(engineWorker -> {
            try {
                moveToFenPositionIfDefined(engineWorker, fen);
                EvaluationParameters evaluationParameters =
                        EvaluationParameters.builder()
                                .depth(10)
                                .build();
                return engineWorker.getPositionEvaluation(evaluationParameters);
            } catch (IOException | TimeoutException e) {
                throw new RuntimeException(e);
            }

        });
    }

    private static void moveToFenPositionIfDefined(ChessEngine engineWorker,
                                                   String fen) throws IOException, TimeoutException {
        if (!Objects.isNull(fen)) {
            engineWorker.moveToFenPosition(fen, true);
        } else {
            engineWorker.moveToStartPosition(true);
        }
    }
}
