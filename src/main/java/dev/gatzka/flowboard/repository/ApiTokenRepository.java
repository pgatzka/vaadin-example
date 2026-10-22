package dev.gatzka.flowboard.repository;

import dev.gatzka.flowboard.domain.ApiToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    List<ApiToken> findAllByUsernameOrderByCreatedAtDesc(String username);

    Optional<ApiToken> findByIdAndUsername(long id, String username);
}
