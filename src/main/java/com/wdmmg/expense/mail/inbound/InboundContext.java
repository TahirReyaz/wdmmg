package com.wdmmg.expense.mail.inbound;

import com.wdmmg.expense.user.User;

/** What a handler gets: the message and, when the sender is a verified user, that user. */
public record InboundContext(ReceivedEmail email, User user) {
    public boolean fromKnownUser() {
        return user != null;
    }
}
