VPN to Socket
===========

Quick and easy VPN to Socket using packet manipulation. Create proxys and firewalls easily globally!

How can I create a proxy or firewall within this library? 
-----------
All you need to do is modify the ```VPN/Proxy.java``` file.
[Proxy.java](https://github.com/DrBrad/Android-VPN-to-Socket/blob/master/app/src/main/java/vpntosocket/shadowrouter/org/vpntosocket/VPN/Proxy.java)

How it works
-----------
VPNs and Sockets are on 2 different layers which makes this project a little bit difficult, however the task is not impossible. This project works by sorting packets based off of type: UDP, TCP, ICMTP. We then take all TCP packets and sort them using a NAT, this makes it easier to identify where each packet is supposed to go. Once the packets are sorted we will take the TCP packets and change the IP address and port to a local socket so that you can do whatever you want with the socket.

License
-----------
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR ANYONE DISTRIBUTING THE SOFTWARE BE LIABLE FOR ANY DAMAGES OR OTHER LIABILITY, WHETHER IN CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE
