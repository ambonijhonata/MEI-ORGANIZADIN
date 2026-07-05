package com.api.client;

import com.api.calendar.CalendarEventRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import com.api.user.UserRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ClientService {
    private static final String USER_NOT_FOUND = "User not found";
    private static final String CLIENT_NOT_FOUND = "Client not found";
    private static final String DUPLICATE_SUFFIX = " Já cadastrado.";

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final CalendarEventRepository eventRepository;
    private final ServiceDescriptionNormalizer normalizer;

    public ClientService(
            final ClientRepository clientRepository,
            final UserRepository userRepository,
            final CalendarEventRepository eventRepository,
            final ServiceDescriptionNormalizer normalizer) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.normalizer = normalizer;
    }

    @Transactional
    public Client createClient(final Long userId, final ClientRequest request) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

        final String normalizedName = normalizer.normalize(request.name());
        final boolean alreadyRegistered = clientRepository.existsByUserIdAndNormalizedName(userId, normalizedName);
        if (alreadyRegistered) {
            throw new BusinessException(request.name().trim() + DUPLICATE_SUFFIX);
        }
        final Client client = new Client(user, request.name(), normalizedName);
        client.updatePersonalInfo(
                request.cpf(),
                request.dateOfBirth(),
                request.email(),
                request.phone()
        );

        return clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public List<Client> listClients(final Long userId, final String name, final Sort sort) {
        List<Client> clients = clientRepository.findByUserId(userId, sort);
        if (name != null && !name.isBlank()) {
            clients = clientRepository.findByUserIdAndNameStartsWithIgnoreCase(userId, name.trim(), sort);
        }
        return clients;
    }

    @Transactional(readOnly = true)
    public Page<Client> listClientsPaginated(
            final Long userId,
            final String name,
        final int pageIndex,
        final int itemsPerPage,
        final Sort sort) {
        final PageRequest pageable = PageRequest.of(pageIndex - 1, itemsPerPage, sort);
        Page<Client> clientPage = clientRepository.findByUserId(userId, pageable);
        if (name != null && !name.isBlank()) {
            clientPage = clientRepository.findByUserIdAndNameStartsWithIgnoreCase(userId, name.trim(), pageable);
        }
        return clientPage;
    }

    @Transactional(readOnly = true)
    public Client getClient(final Long userId, final Long clientId) {
        return clientRepository.findByIdAndUserId(clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(CLIENT_NOT_FOUND));
    }

    @Transactional
    public Client updateClient(final Long userId, final Long clientId, final ClientRequest request) {
        final Client client = clientRepository.findByIdAndUserId(clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(CLIENT_NOT_FOUND));

        final String normalizedName = normalizer.normalize(request.name());
        final boolean alreadyRegistered = clientRepository.existsByUserIdAndNormalizedNameAndIdNot(
                userId,
                normalizedName,
                clientId
        );
        if (alreadyRegistered) {
            throw new BusinessException(request.name().trim() + DUPLICATE_SUFFIX);
        }
        client.rename(request.name(), normalizedName);
        client.updatePersonalInfo(
                request.cpf(),
                request.dateOfBirth(),
                request.email(),
                request.phone()
        );

        return clientRepository.save(client);
    }

    @Transactional
    public void deleteClient(final Long userId, final Long clientId) {
        final Client client = clientRepository.findByIdAndUserId(clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(CLIENT_NOT_FOUND));

        final boolean hasLinkedEvents = eventRepository.existsByClientId(clientId);
        if (hasLinkedEvents) {
            throw new BusinessException("Cannot delete client with linked appointments");
        }

        clientRepository.delete(client);
    }

    public Client findOrCreateByName(final Long userId, final User user, final String clientName) {
        final String normalizedName = normalizer.normalize(clientName);
        return clientRepository.findByUserIdAndNormalizedName(userId, normalizedName)
                .orElseGet(() -> clientRepository.save(new Client(user, clientName.trim(), normalizedName)));
    }

    @Transactional(readOnly = true)
    public Optional<Client> findByNormalizedName(final Long userId, final String normalizedName) {
        return clientRepository.findByUserIdAndNormalizedName(userId, normalizedName);
    }

    @Transactional(readOnly = true)
    public Map<String, Client> listClientsByNormalizedName(final Long userId) {
        final Map<String, Client> clients = new LinkedHashMap<>();
        for (final Client client : clientRepository.findAllByUserId(userId)) {
            clients.putIfAbsent(client.getNormalizedName(), client);
        }
        return clients;
    }

    @Transactional
    public BulkDeleteResult bulkDeleteClients(final Long userId, final List<Long> clientIds) {
        int deleted = 0;
        int linkedClients = 0;

        for (final Long clientId : clientIds) {
            final Optional<Client> optionalClient = clientRepository.findByIdAndUserId(clientId, userId);
            if (optionalClient.isEmpty()) {
                continue;
            }
            if (eventRepository.existsByClientId(clientId)) {
                linkedClients++;
            } else {
                clientRepository.delete(optionalClient.get());
                deleted++;
            }
        }

        return new BulkDeleteResult(deleted, linkedClients);
    }

    public record BulkDeleteResult(int deleted, int hasLink) {}

    public record ClientRequest(
            String name,
            String cpf,
            LocalDate dateOfBirth,
            String email,
            String phone
    ) {}
}
