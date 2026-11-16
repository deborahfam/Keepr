package com.keepr.app.domain;

/** Published contact channels. Empty fields mean unknown, not inferred from another channel. */
public record ContactDetails(String name, String phone, String email, String whatsapp, String source) {
    public static final ContactDetails EMPTY=new ContactDetails("","","","","");
    public boolean isEmpty() { return name.isEmpty() && phone.isEmpty() && email.isEmpty() && whatsapp.isEmpty(); }
}
