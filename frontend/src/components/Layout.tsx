import { type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Mic, LogOut, Users, Send, Library, BarChart3 } from "lucide-react";

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login");
  }

  const isAdmin = user?.role === "ADMIN";
  const canDistribute = ["ADMIN", "PRODUCER", "OPERATOR"].includes(user?.role ?? "");
  const canLibraryStats = ["ADMIN", "PRODUCER", "EDITOR", "HOST", "OPERATOR"].includes(user?.role ?? "");

  return (
    <div className="min-h-screen bg-muted/30">
      <header className="border-b bg-background">
        <div className="container flex h-14 items-center justify-between">
          <Link to="/" className="flex items-center gap-2 font-semibold">
            <Mic className="h-5 w-5 text-primary" />
            播客制作协作
          </Link>
          <div className="flex items-center gap-3 text-sm">
            {canDistribute && (
              <Link to="/distribution" className="flex items-center gap-1 text-muted-foreground hover:text-foreground">
                <Send className="h-4 w-4" /> 分发
              </Link>
            )}
            {canLibraryStats && (
              <Link to="/assets" className="flex items-center gap-1 text-muted-foreground hover:text-foreground">
                <Library className="h-4 w-4" /> 素材库
              </Link>
            )}
            {canLibraryStats && (
              <Link to="/stats" className="flex items-center gap-1 text-muted-foreground hover:text-foreground">
                <BarChart3 className="h-4 w-4" /> 统计
              </Link>
            )}
            {isAdmin && (
              <Link to="/members" className="flex items-center gap-1 text-muted-foreground hover:text-foreground">
                <Users className="h-4 w-4" /> 团队成员
              </Link>
            )}
            <span className="text-muted-foreground">
              {user?.name}（{user?.role}）
            </span>
            <Button variant="ghost" size="sm" onClick={handleLogout}>
              <LogOut className="h-4 w-4" /> 退出
            </Button>
          </div>
        </div>
      </header>
      <main className="container py-6">{children}</main>
    </div>
  );
}
