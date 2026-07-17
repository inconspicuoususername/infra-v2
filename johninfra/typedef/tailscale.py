from typing import Any, Dict, List, Optional
from datetime import datetime
from pydantic import BaseModel, Field


class NodeInfo(BaseModel):
    """Represents a Tailscale node (self or peer)."""
    ID: str
    PublicKey: str
    HostName: str
    DNSName: str
    OS: str
    UserID: int
    TailscaleIPs: List[str]
    AllowedIPs: List[str]
    Addrs: Optional[List[str]] = None
    CurAddr: str = ""
    Relay: str
    PeerRelay: str = ""
    RxBytes: int = 0
    TxBytes: int = 0
    Created: datetime
    LastWrite: datetime
    LastSeen: datetime
    LastHandshake: datetime
    Online: bool
    ExitNode: bool = False
    ExitNodeOption: bool = False
    Active: bool = False
    PeerAPIURL: List[str]
    TaildropTarget: int = 0
    NoFileSharingReason: str = ""
    Capabilities: Optional[List[str]] = None
    CapMap: Optional[Dict[str, Optional[List[Any]]]] = None
    KeyExpiry: Optional[datetime] = None
    InNetworkMap: bool = False
    InMagicSock: bool = False
    InEngine: bool = False


class TailnetInfo(BaseModel):
    """Current Tailnet information."""
    Name: str
    MagicDNSSuffix: str
    MagicDNSEnabled: bool


class UserInfo(BaseModel):
    """Tailscale user information."""
    ID: int
    LoginName: str
    DisplayName: str
    ProfilePicURL: Optional[str] = None


class TailscaleStatus(BaseModel):
    """Root Tailscale status structure from `tailscale status --json`."""
    Version: str
    TUN: bool
    BackendState: str
    HaveNodeKey: bool
    AuthURL: str = ""
    TailscaleIPs: List[str]
    Self: NodeInfo
    Health: List[Any] = Field(default_factory=list)
    MagicDNSSuffix: str
    CurrentTailnet: TailnetInfo
    CertDomains: Optional[List[str]] = None
    Peer: Dict[str, NodeInfo] = Field(default_factory=dict)
    User: Dict[int, UserInfo] = Field(default_factory=dict)
    ClientVersion: Optional[str] = None
