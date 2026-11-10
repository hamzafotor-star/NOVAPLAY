using System;
using System.Diagnostics;
using System.IO;
using System.Net.Sockets;
using System.Threading;
using System.Windows.Forms;

namespace NovaPlayIPTV
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string serverScript = Path.Combine(baseDir, "pc", "server.js");
            if (!File.Exists(serverScript))
            {
                serverScript = Path.Combine(baseDir, "server.js");
            }

            if (!File.Exists(serverScript))
            {
                MessageBox.Show(
                    "Fichier pc\\server.js introuvable.\nAssurez-vous que NovaPlayIPTV-PC.exe se trouve dans le dossier du projet.",
                    "NovaPlay IPTV PC",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
                return;
            }

            int port = 4789;
            Process serverProc = null;

            if (!IsPortOpen("127.0.0.1", port))
            {
                try
                {
                    var psi = new ProcessStartInfo
                    {
                        FileName = "node",
                        Arguments = "\"" + serverScript + "\"",
                        WorkingDirectory = Path.GetDirectoryName(serverScript),
                        UseShellExecute = false,
                        CreateNoWindow = true,
                        WindowStyle = ProcessWindowStyle.Hidden
                    };
                    serverProc = Process.Start(psi);
                }
                catch (Exception ex)
                {
                    MessageBox.Show(
                        "Impossible de démarrer le moteur Node.js : " + ex.Message,
                        "NovaPlay IPTV PC",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Error
                    );
                    return;
                }

                // Wait up to 4 seconds for local server to listen
                for (int i = 0; i < 40; i++)
                {
                    if (IsPortOpen("127.0.0.1", port)) break;
                    Thread.Sleep(100);
                }
            }

            string appUrl = "http://127.0.0.1:" + port;
            OpenDesktopWindow(appUrl);
        }

        static bool IsPortOpen(string host, int port)
        {
            try
            {
                using (var client = new TcpClient())
                {
                    var result = client.BeginConnect(host, port, null, null);
                    bool success = result.AsyncWaitHandle.WaitOne(TimeSpan.FromMilliseconds(150));
                    if (!success) return false;
                    client.EndConnect(result);
                    return true;
                }
            }
            catch
            {
                return false;
            }
        }

        static void OpenDesktopWindow(string url)
        {
            string[] browserCandidates = new string[]
            {
                @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
                @"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
                @"C:\Program Files\Google\Chrome\Application\chrome.exe",
                @"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
            };

            foreach (string browserPath in browserCandidates)
            {
                if (File.Exists(browserPath))
                {
                    var psi = new ProcessStartInfo
                    {
                        FileName = browserPath,
                        Arguments = "--app=" + url + " --window-size=1440,860",
                        UseShellExecute = true
                    };
                    Process.Start(psi);
                    return;
                }
            }

            // Fallback to default browser
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
    }
}
