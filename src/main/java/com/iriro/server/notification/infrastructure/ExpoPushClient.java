package com.iriro.server.notification.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class ExpoPushClient implements ExpoPushGateway {
    private final RestClient restClient;

    @Autowired
    public ExpoPushClient(@Value("${iriro.notification.expo.base-url:https://exp.host}") String baseUrl) {
        this(RestClient.builder(), baseUrl);
    }

    ExpoPushClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @Override
    public List<ExpoPushTicket> send(List<ExpoPushMessage> messages) {
        if (messages.isEmpty()) return List.of();
        List<ExpoPushTicket> tickets = new java.util.ArrayList<>(messages.size());
        for (int start = 0; start < messages.size(); start += 100) {
            tickets.addAll(sendChunk(messages.subList(start, Math.min(start + 100, messages.size()))));
        }
        return List.copyOf(tickets);
    }

    private List<ExpoPushTicket> sendChunk(List<ExpoPushMessage> messages) {
        TicketResponse response;
        try {
            response = restClient.post()
                .uri("/--/api/v2/push/send")
                .contentType(MediaType.APPLICATION_JSON)
                .body(messages)
                .retrieve()
                .body(TicketResponse.class);
        } catch (RestClientException exception) {
            throw new ExpoPushServiceException("Expo push send request failed", exception);
        }
        if (response == null || response.data() == null || response.data().size() != messages.size()) {
            throw new ExpoPushServiceException("Expo returned an invalid ticket response");
        }
        return response.data();
    }

    @Override
    public Map<String, ExpoPushReceipt> getReceipts(List<String> ticketIds) {
        if (ticketIds.isEmpty()) return Map.of();
        Map<String, ExpoPushReceipt> receipts = new java.util.LinkedHashMap<>();
        for (int start = 0; start < ticketIds.size(); start += 1000) {
            receipts.putAll(receiptChunk(ticketIds.subList(start, Math.min(start + 1000, ticketIds.size()))));
        }
        return Map.copyOf(receipts);
    }

    private Map<String, ExpoPushReceipt> receiptChunk(List<String> ticketIds) {
        ReceiptResponse response;
        try {
            response = restClient.post()
                .uri("/--/api/v2/push/getReceipts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ReceiptRequest(ticketIds))
                .retrieve()
                .body(ReceiptResponse.class);
        } catch (RestClientException exception) {
            throw new ExpoPushServiceException("Expo push receipt request failed", exception);
        }
        if (response == null) throw new ExpoPushServiceException("Expo returned an empty receipt response");
        return response.data() == null ? Map.of() : response.data();
    }

    private record TicketResponse(List<ExpoPushTicket> data) { }
    private record ReceiptRequest(List<String> ids) { }
    private record ReceiptResponse(Map<String, ExpoPushReceipt> data) { }
}
