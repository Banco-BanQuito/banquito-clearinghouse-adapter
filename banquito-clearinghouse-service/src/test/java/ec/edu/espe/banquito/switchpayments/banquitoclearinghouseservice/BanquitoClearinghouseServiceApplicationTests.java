package ec.edu.espe.banquito.switchpayments.banquitoclearinghouseservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BanquitoClearinghouseServiceApplicationTests {

    @Test
    void mainClass_debeEstarDisponible() {
        assertThat(BanquitoClearinghouseServiceApplication.class).isNotNull();
    }
}
