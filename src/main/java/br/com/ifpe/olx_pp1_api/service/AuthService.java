package br.com.ifpe.olx_pp1_api.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.ifpe.olx_pp1_api.config.JwtService;
import br.com.ifpe.olx_pp1_api.dto.AuthResponse;
import br.com.ifpe.olx_pp1_api.dto.LoginRequest;
import br.com.ifpe.olx_pp1_api.dto.RegisterRequest;
import br.com.ifpe.olx_pp1_api.modelo.Endereco;
import br.com.ifpe.olx_pp1_api.modelo.Role;
import br.com.ifpe.olx_pp1_api.modelo.Usuario;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request, Role role) {
        
        
        if (usuarioService.existsByCpfCnpj(request.getCpfCnpj())) {
            throw new RuntimeException("CPF/CNPJ já está em uso!");
        }

        
        Set<Role> roles = new HashSet<>();
        roles.add(role); 
        
        if (role == Role.ROLE_VENDEDOR) {
            roles.add(Role.ROLE_COMPRADOR);
            
            
            if (request.getCep() == null || request.getLogradouro() == null) {
                throw new RuntimeException("Vendedores devem informar o endereço completo (CEP e Logradouro).");
            }
        }

        
        Usuario usuario = Usuario.builder()
                .nome(request.getNome())
                .email(request.getEmail())
                .senha(request.getSenha()) 
                .cpfCnpj(request.getCpfCnpj())
                .telefone(request.getTelefone())
                .dataNascimento(request.getDataNascimento())
                .roles(roles) 
                .build();
        
       
        if (request.getCep() != null) {
            Endereco endereco = Endereco.builder()
                    .cep(request.getCep())
                    .logradouro(request.getLogradouro())
                    .numero(request.getNumero())
                    .bairro(request.getBairro())
                    .cidade(request.getCidade())
                    .uf(request.getUf())
                    .complemento(request.getComplemento())
                    .build();
            
            usuario.setEndereco(endereco);
        }

        
        Usuario usuarioSalvo = usuarioService.registrarUsuario(usuario);
        
        return AuthResponse.builder()
                .token(null) 
                .nomeUsuario(usuarioSalvo.getNome())
                .build();
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public AuthResponse login(LoginRequest request) {
        
        Usuario usuario = usuarioService.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (!usuario.isHabilitado()) {
            usuarioService.reenviarCodigoAtivacao(usuario);
            throw new RuntimeException("Conta não ativada. Um novo link de confirmação foi enviado para o seu e-mail.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getSenha()
                )
        );
        
        UserDetails userDetails = buildUserDetails(usuario);
        String token = jwtService.generateToken(userDetails);
        
        return AuthResponse.builder()
                .token(token)
                .nomeUsuario(usuario.getNome())
                .build();
    }
    
    private UserDetails buildUserDetails(Usuario usuario) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenha())
                .roles(usuario.getRoles().stream()
                        .map(r -> r.name().replace("ROLE_", ""))
                        .toArray(String[]::new))
                .build();
    }
}