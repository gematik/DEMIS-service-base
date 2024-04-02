/*
 * Copyright [2024], gematik GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package de.gematik.demis.service.base.logging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final String EXCLUDED_URI = "/actuator";

  @PostConstruct
  void log() {
    log.info("RequestLoggingFilter activated");
  }

  @Override
  protected void doFilterInternal(
      @Nonnull final HttpServletRequest request,
      @Nonnull final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final long startTime = System.currentTimeMillis();
    filterChain.doFilter(request, response);
    final long executionTime = System.currentTimeMillis() - startTime;

    final String requestURI = request.getRequestURI();

    if (!requestURI.startsWith(EXCLUDED_URI)) {
      log.info(
          "request {} '{}' --> status {}, time {}ms",
          request.getMethod(),
          requestURI,
          response.getStatus(),
          executionTime);
    }
  }
}
