package com.medplus.marketing_automation_backend.security;

import com.medplus.marketing_automation_backend.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Authenticates STOMP CONNECT frames by validating the JWT passed in the
 * {@code Authorization} header of the frame (not the HTTP handshake header).
 *
 * <p>Frontend must send the token as a STOMP connect header:
 * <pre>
 *   client.connectHeaders = { Authorization: 'Bearer <token>' }
 * </pre>
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider         tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    public JwtChannelInterceptor(JwtTokenProvider tokenProvider,
                                 CustomUserDetailsService userDetailsService) {
        this.tokenProvider     = tokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        // Only validate on CONNECT — subsequent frames inherit the authenticated principal
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                throw new IllegalArgumentException("Missing or invalid Authorization header in STOMP CONNECT.");
            }
            String token = authHeader.substring(7);
            try {
                String email = tokenProvider.getEmail(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                accessor.setUser(auth);
            } catch (JwtException | IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid or expired JWT token.", ex);
            }
        }
        return message;
    }
}
