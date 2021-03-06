package de.bornemisza.ds.users.subscriber;

import de.bornemisza.ds.users.subscriber.AbstractConfirmationMailListener;
import de.bornemisza.ds.users.subscriber.NewUserAccountListener;
import java.io.IOException;

import javax.mail.NoSuchProviderException;
import javax.mail.internet.AddressException;

import org.junit.Test;

import com.hazelcast.core.HazelcastInstance;

import de.bornemisza.ds.users.MailSender;

public class NewUserAccountListenerTest extends AbstractConfirmationMailListenerTestbase {

    @Override
    AbstractConfirmationMailListener getRequestListener(MailSender mailSender, HazelcastInstance hz) {
        return new NewUserAccountListener(mailSender, hz);
    }

    @Override
    String getConfirmationLinkPrefix() {
        return "https://" + System.getProperty("FQDN") + "/generic.html?action=confirm&type=user&uuid=";
    }

    @Test
    public void onMessage_mailSent_styledTemplate() throws AddressException, NoSuchProviderException, IOException {
        onMessage_mailSent_styledTemplate_Base();
    }

    @Test
    public void onMessage_mailNotSent() throws AddressException, NoSuchProviderException, IOException {
        onMessage_mailNotSent_Base();
    }

    @Test
    public void onMessage_uuidExists_doNotSendAdditionalMail_unchangedValue() throws AddressException, NoSuchProviderException {
        onMessage_userExists_doNotSendAdditionalMail_Base();
    }

    @Test
    public void onMessage_uuidExists_doNotSendAdditionalMail_locked() throws AddressException, NoSuchProviderException {
        onMessage_uuidExists_doNotSendAdditionalMail_locked_Base();
    }

    @Test
    public void onMessage_uuidExists_sendAdditionalMailIfAddressDifferent() throws AddressException, NoSuchProviderException {
        onMessage_uuidExists_sendAdditionalMail_Base();
    }

}
