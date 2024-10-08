package com.br.domain.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.br.domain.enums.RoleType;
import com.br.domain.model.Role;
import com.br.domain.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.br.core.config.Generator;
import com.br.domain.exception.EntidadeNaoExisteException;
import com.br.domain.model.User;
import com.br.domain.repository.UserRepository;
import com.br.domain.service.UserService;
import com.br.infrastructure.externalservice.rest.department.DepartmentFeignClient;
import com.br.infrastructure.externalservice.rest.department.model.Department;
import com.br.infrastructure.externalservice.rest.notification.NotificationFeignClient;
import com.br.infrastructure.externalservice.rest.notification.model.Mensagem;

@Service
public class UserServiceImpl implements UserService {
	
	private static final int TAMANHO_SENHA = 8;

	@Autowired 
	private UserRepository userRepository;
	
	@Autowired
	private DepartmentFeignClient departmentFeignClient;
	
	@Autowired
	private NotificationFeignClient notificationFeignClient;
	
	@Autowired
	private Generator generator;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RoleService roleService;
	
	@Override
	public User save(User user) {
		Department department = departmentFeignClient.getDepartment(user.getDepartmentId());
		User userSave = userRepository.save(user);
		String matricula = getMatricula(department, userSave.getUserId());
		String password = generator.password(TAMANHO_SENHA);
		Role role = roleService.findByRoleName(RoleType.ROLE_FUNCIONARIO)
				.orElseThrow(() -> new RuntimeException("Error: Permissão 'ROLE_FUNCIONARIO´  não existe."));
		String passwordEncode = passwordEncoder.encode(password);
		System.out.println(">>>>>>>>>>>>>>>>> SENHA: " + password);
		System.out.println(">>>>>>>>>>>>>>>>> MATRICULA: " + matricula);
		userSave.setMatricula(matricula);
		userSave.setPassword(passwordEncode);
		userSave.getRoles().add(role);
		userSave = userRepository.save(userSave);
		notificationFeignClient.registryUser(new Mensagem(
				user.getEmail(), 
				matricula, 
				password, 
				user.getNome())
				);
		
		return userSave;
	}

	private String getMatricula(Department department, UUID id) {
        generator.sigla(id);
		return generator.getSigla(department.getSigla());
	}

	@Override
	public Page<User> findAll(Specification<User> spec, Pageable pageable) {
		return userRepository.findAll(spec, pageable);
	}

	@Override
	public User findById(UUID id) {
		Optional<User> user = userRepository.findById(id);
		if(user.isEmpty()) {
			throw new EntidadeNaoExisteException("Usuário informado não existe: " + id);
		}
		return user.get();
	}

	@Override
	public User findByEmail(String email) {
		Optional<User> user = userRepository.findByEmail(email);
		if(user.isEmpty()) {
			throw new EntidadeNaoExisteException("E-mail informado nao existe: " + email);
		}
		return user.get();
	}
	
	@Override
	public User findByMatricula(String matricula) {
		Optional <User> user = userRepository.findByMatriculaSearch(matricula);
		if(user.isEmpty()) {
			throw new EntidadeNaoExisteException("Matricula não existe: " + matricula);
		}
		return user.get();
	}

	@Override
	public List<User> buscarUsuariosDoDepartamento(UUID departmentId) {
		return userRepository.buscarUsuariosDoDepartamento(departmentId);
	}

	@Override
    public User deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        user.setActive(false);
        return userRepository.save(user);
	}

	@Override
	public User activaUser(UUID id, Boolean active) {
			User user = userRepository.findById(id)
		                .orElseThrow(() -> new RuntimeException("Usuario não encontrado."));
			user.setActive(active);
		return userRepository.save(user);
	}

	@Override
	public Page<User> Filtro(String matricula, String nome, UUID departmentId, Pageable pageable) {
		return userRepository.Filtro(matricula, nome, departmentId, pageable);
	}
	
	 
    public void processForgotPassword(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user == null) {
            throw new EntidadeNaoExisteException("Email não encontrada");
        }
        
        String newPassword = generator.password(TAMANHO_SENHA);
        String encryptedPassword = passwordEncoder.encode(newPassword);
        User novoUsu = user.get();
        novoUsu.setPassword(encryptedPassword);
        userRepository.save(novoUsu);
        sendEmail(novoUsu.getEmail(), newPassword); 
    }
    
    private void sendEmail(String email, String password) {
    	User user = new User();
    	notificationFeignClient.registryUser(new Mensagem(
				user.getEmail(), 
				user.getMatricula(), 
				password, 
				user.getNome()) 
				);
    }
	
}
