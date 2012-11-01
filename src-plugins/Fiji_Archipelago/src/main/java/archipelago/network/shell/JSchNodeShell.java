package archipelago.network.shell;

import archipelago.EasyLogger;
import archipelago.ShellExecListener;
import archipelago.StreamLogger;
import archipelago.network.ClusterNode;
import com.jcraft.jsch.*;
import ij.IJ;
import ij.gui.GenericDialog;

import java.io.File;
import java.io.IOException;

public class JSchNodeShell implements NodeShell
{

    private static class JSNUserInfo implements UserInfo
    {

        private String passphrase = "";
        private boolean displayEnabled = true;
        private boolean passSet = false;

        public String getPassphrase() {
            return passphrase;
        }

        public String getPassword() {
            return null;
        }

        public boolean promptPassword(String s) {
            return true;
        }

        public boolean promptPassphrase(String s) {
            if (displayEnabled && !passSet)
            {
                GenericDialog gd = new GenericDialog("Enter Passphrase");

                gd.addStringField("Please enter the public key passphrase", "");

                gd.showDialog();

                passphrase = gd.getNextString();

                passSet = true;
            }
            return true;
        }

        public boolean promptYesNo(String s) {
            return true;
        }

        public void showMessage(String s)
        {
            if (displayEnabled)
            {
                IJ.showMessage(s);
            }
        }

        public void enableDisplay()
        {
            displayEnabled = true;
        }

        public void disableDisplay()
        {
            displayEnabled = false;
        }

        public void unsetPass()
        {
            passSet = false;
        }

    }

    public static class JSchShellParams
    {
        private int port;
        private UserInfo ui;
        private JSch jsch;
        
        public JSchShellParams(File f)
        {
            this(f, 22);
        }
        
        public JSchShellParams(File f, int p)
        {
            port = p;
            ui = new JSNUserInfo();
            jsch = new JSch();
            addKey(f);
        }
        
        public boolean addKey(File f)
        {
            try
            {
                jsch.addIdentity(f.getAbsolutePath());
                return true;
            }
            catch (JSchException jse)
            {
                return false;
            }
        }
        
        public int getPort()
        {
            return port;
        }
        
        public UserInfo getUserInfo()
        {
            return ui;
        }
        

        
        public JSch getJsch()
        {
            return jsch;
        }
    }

    private class JSchShellExecThread extends Thread
    {
        private final ClusterNode node;
        private final Channel channel;
        private final ShellExecListener listener;
        private final Session session;


        public JSchShellExecThread(ClusterNode n, Channel c, Session s, ShellExecListener l)
        {
            node = n;
            channel = c;
            listener = l;
            session = s;
        }

        public void run()
        {
            try
            {
                channel.connect();
                
                while (!channel.isClosed())
                {
                    Thread.sleep(1000);
                }

                listener.execFinished(node, null);
            }
            catch (JSchException jse)
            {
                listener.execFinished(node, jse);
            }
            catch (InterruptedException ie)
            {
                listener.execFinished(node, ie);
            }

            channel.disconnect();
            session.disconnect();
        }

    }
    
    private final JSchShellParams params;
    private final StreamLogger logger;
    
    public JSchNodeShell(JSchShellParams p, EasyLogger l)
    {
        params = p;
        logger = new StreamLogger(l);
    }
    
    public boolean exec(ClusterNode node, String command, ShellExecListener listener)
    {
        try
        {
            Session session = params.getJsch().getSession(node.getUser(), node.getHost());
            Channel channel;
            
            session.setUserInfo(params.getUserInfo());
            session.connect();
            
            channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);
            
            channel.setInputStream(null);
            ((ChannelExec)channel).setErrStream(System.err);
            
            try
            {
                logger.setStream(channel.getInputStream());
            }
            catch (IOException ioe)
            {
                logger.setStream(null);
            }
            
            new JSchShellExecThread(node, channel, session, listener).start();
            
            return true;
        }
        catch (JSchException jse)
        {
            return false;
        }
        
    }
}
