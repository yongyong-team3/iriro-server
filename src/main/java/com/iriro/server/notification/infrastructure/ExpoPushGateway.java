package com.iriro.server.notification.infrastructure;

import java.util.List;
import java.util.Map;

public interface ExpoPushGateway {
    List<ExpoPushTicket> send(List<ExpoPushMessage> messages);
    Map<String, ExpoPushReceipt> getReceipts(List<String> ticketIds);
}
