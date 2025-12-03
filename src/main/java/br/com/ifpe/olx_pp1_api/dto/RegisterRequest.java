package br.com.ifpe.olx_pp1_api.dto;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class RegisterRequest {
    @NotBlank(message = "O nome é obrigatório") private String nome;
    @NotBlank(message = "O email é obrigatório") @Email(message = "Insira um email válido") private String email;
    @NotBlank(message = "A senha é obrigatória") @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres") private String senha;
    @NotBlank(message = "O CPF/CNPJ é obrigatório") private String cpfCnpj;
    private String telefone;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate dataNascimento;

    private String cep;
    private String logradouro;
    private String numero;
    private String bairro;
    private String cidade;
    private String uf;
    private String complemento;
}