package com.api.client;

import com.api.calendar.CalendarEventRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import com.api.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final ServiceDescriptionNormalizer normalizer;

    public ClientService(ClientRepository clientRepository,
                          UserRepository userRepository,
                          CalendarEventRepository calendarEventRepository,
                          ServiceDescriptionNormalizer normalizer) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.normalizer = normalizer;
    }

    @Transactional
    public Client createClient(Long userId, ClientRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String normalized = normalizer.normalize(request.name());
        Client client = new Client(user, request.name(), normalized);
        client.setCpf(request.cpf());
        client.setDateOfBirth(request.dateOfBirth());
        client.setEmail(request.email());
        client.setPhone(request.phone());

        return clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public List<Client> listClients(Long userId, String name, Sort sort) {
        if (name != null && !name.isBlank()) {
            return clientRepository.findByUserIdAndNameContainingIgnoreCase(userId, name, sort);
        }
        return clientRepository.findByUserId(userId, sort);
    }

    @Transactional(readOnly = true)
    public Client getClient(Long userId, Long clientId) {
        return clientRepository.findByIdAndUserId(clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    @Transactional
    public Client updateClient(Long userId, Long clientId, ClientRequest request) {
        Client client = clientRepository.findByIdAndUserId(clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        client.setName(request.name());
        client.setNormalizedName(normalizer.normalize(request.name()));
        client.setCpf(request.cpf());
        client.setDateOfBirth(request.dateOfBirth());
        client.setEmail(request.email());
        client.setPhone(request.phone());

        return clientRepository.save(client);
    }

    @Transactional
    public void deleteClient(Long userId, Long clientId) {
        Client client = clientRepository.findByIdAndUserId(clientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        boolean hasLinkedEvents = calendarEventRepository.existsByClientId(clientId);
        if (hasLinkedEvents) {
            throw new BusinessException("Cannot delete client with linked appointments");
        }

        clientRepository.delete(client);
    }

    public Client findOrCreateByName(Long userId, User user, String clientName) {
        String normalized = normalizer.normalize(clientName);
        return clientRepository.findByUserIdAndNormalizedName(userId, normalized)
                .orElseGet(() -> clientRepository.save(new Client(user, clientName.trim(), normalized)));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Client> findByNormalizedName(Long userId, String normalizedName) {
        return clientRepository.findByUserIdAndNormalizedName(userId, normalizedName);
    }

    public record ClientRequest(
            String name,
            String cpf,
            java.time.LocalDate dateOfBirth,
            String email,
            String phone
    ) {}
}
