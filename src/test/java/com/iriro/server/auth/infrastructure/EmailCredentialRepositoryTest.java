package com.iriro.server.auth.infrastructure;

import com.iriro.server.auth.domain.EmailCredential;
import com.iriro.server.member.domain.Member;
import com.iriro.server.member.infrastructure.MemberRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EmailCredentialRepositoryTest.JpaTestConfiguration.class)
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                TransactionalTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
@Transactional
class EmailCredentialRepositoryTest {

    private final MemberRepository memberRepository;
    private final EmailCredentialRepository emailCredentialRepository;

    @Autowired
    EmailCredentialRepositoryTest(
            MemberRepository memberRepository,
            EmailCredentialRepository emailCredentialRepository
    ) {
        this.memberRepository = memberRepository;
        this.emailCredentialRepository = emailCredentialRepository;
    }

    @Test
    void savesMemberAndEmailCredentialWithSharedPrimaryKey() {
        Member member = memberRepository.saveAndFlush(new Member("영환", "영환"));
        EmailCredential credential = emailCredentialRepository.saveAndFlush(
                new EmailCredential(member, "younghwan@example.com", "test-hash")
        );

        EmailCredential loaded = emailCredentialRepository.findById(member.getId()).orElseThrow();

        assertEquals(member.getId(), credential.getMemberId());
        assertEquals(member.getId(), loaded.getMember().getId());
        assertEquals("younghwan@example.com", loaded.getEmailNormalized());
        assertEquals("test-hash", loaded.getPasswordHash());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void rejectsDuplicateNormalizedEmail() {
        Member firstMember = memberRepository.saveAndFlush(new Member("영환", "영환"));
        emailCredentialRepository.saveAndFlush(
                new EmailCredential(firstMember, "same@example.com", "test-hash-1")
        );

        Member secondMember = memberRepository.saveAndFlush(new Member("영환2", "영환2"));
        EmailCredential duplicate = new EmailCredential(
                secondMember,
                "same@example.com",
                "test-hash-2"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> emailCredentialRepository.saveAndFlush(duplicate)
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {
            MemberRepository.class,
            EmailCredentialRepository.class
    })
    static class JpaTestConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:iriro-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    "com.iriro.server.member.domain",
                    "com.iriro.server.auth.domain"
            );
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of(
                    "hibernate.hbm2ddl.auto", "none",
                    "hibernate.dialect", "org.hibernate.dialect.H2Dialect"
            ));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
