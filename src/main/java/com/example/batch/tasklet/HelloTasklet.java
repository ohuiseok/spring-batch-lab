package com.example.batch.tasklet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HelloTasklet implements Tasklet {

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // Chapter 1-1 focuses on the Tasklet lifecycle, so this task only writes a simple log.
        log.info("Hello, Batch!");
        // FINISHED tells Spring Batch that this Tasklet Step does not need to repeat.
        return RepeatStatus.FINISHED;
    }
}
