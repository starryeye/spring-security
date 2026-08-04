package dev.starryeye.session.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientInfo(String clientId, String backchannelLogoutUri) {
}
