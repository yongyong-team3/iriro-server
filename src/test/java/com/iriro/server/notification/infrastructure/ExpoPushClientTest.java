package com.iriro.server.notification.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class ExpoPushClientTest {
    @Test
    void sendsMessagesAndReadsExpoTicketResponseThroughMockHttpServer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://expo.test/--/api/v2/push/send"))
                .andExpect(method(POST))
                .andExpect(content().json("[{\"to\":\"ExponentPushToken[test]\",\"title\":\"title\",\"body\":\"body\",\"data\":null}]"))
                .andRespond(withSuccess("{\"data\":[{\"status\":\"ok\",\"id\":\"ticket-1\"}]}", MediaType.APPLICATION_JSON));
        ExpoPushClient client = new ExpoPushClient(builder, "https://expo.test");

        List<ExpoPushTicket> tickets = client.send(List.of(
                new ExpoPushMessage("ExponentPushToken[test]", "title", "body", null)));

        assertThat(tickets).containsExactly(new ExpoPushTicket("ok", "ticket-1", null, null));
        server.verify();
    }

    @Test
    void readsReceiptResponseAndWrapsHttpFailures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://expo.test/--/api/v2/push/getReceipts"))
                .andExpect(method(POST))
                .andExpect(content().json("{\"ids\":[\"ticket-1\"]}"))
                .andRespond(withSuccess("{\"data\":{\"ticket-1\":{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}}}", MediaType.APPLICATION_JSON));
        ExpoPushClient client = new ExpoPushClient(builder, "https://expo.test");

        assertThat(client.getReceipts(List.of("ticket-1")).get("ticket-1").details().error())
                .isEqualTo("DeviceNotRegistered");
        server.verify();

        RestClient.Builder failingBuilder = RestClient.builder();
        MockRestServiceServer failureServer = MockRestServiceServer.bindTo(failingBuilder).build();
        failureServer.expect(requestTo("https://expo.test/--/api/v2/push/send"))
                .andRespond(withServerError());
        ExpoPushClient failingClient = new ExpoPushClient(failingBuilder, "https://expo.test");
        assertThatThrownBy(() -> failingClient.send(List.of(
                new ExpoPushMessage("ExponentPushToken[test]", "title", "body", null))))
                .isInstanceOf(ExpoPushServiceException.class);
        failureServer.verify();
    }
}
