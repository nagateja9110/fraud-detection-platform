package com.frauddetection.service;

import com.frauddetection.dto.ml.MlPredictionRequest;
import com.frauddetection.dto.ml.MlPredictionResponse;
import com.frauddetection.exception.MlServiceException;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class MlClientService {

    private final WebClient webClient;

    public MlClientService(WebClient mlServiceWebClient) {
        this.webClient = mlServiceWebClient;
    }

    public MlPredictionResponse predict(MlPredictionRequest request) {
        try {
            return webClient.post()
                    .uri("/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MlPredictionResponse.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception e) {
            throw new MlServiceException("Fraud scoring service call failed: " + e.getMessage(), e);
        }
    }
}
