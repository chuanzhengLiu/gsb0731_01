import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function LoginPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [teamName, setTeamName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      if (mode === "login") {
        await login(email, password);
      } else {
        await register(email, name, password, teamName);
      }
      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "操作失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/30 px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>{mode === "login" ? "登录" : "注册团队"}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="email">邮箱</Label>
              <Input id="email" type="email" value={email}
                onChange={(e) => setEmail(e.target.value)} required />
            </div>
            {mode === "register" && (
              <>
                <div className="space-y-2">
                  <Label htmlFor="name">姓名</Label>
                  <Input id="name" value={name}
                    onChange={(e) => setName(e.target.value)} required />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="team">团队名称</Label>
                  <Input id="team" value={teamName}
                    onChange={(e) => setTeamName(e.target.value)} required />
                </div>
              </>
            )}
            <div className="space-y-2">
              <Label htmlFor="password">密码</Label>
              <Input id="password" type="password" value={password}
                onChange={(e) => setPassword(e.target.value)} required />
              {mode === "register" && (
                <p className="text-xs text-muted-foreground">
                  至少10位，需包含字母、数字和特殊字符
                </p>
              )}
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? "处理中..." : mode === "login" ? "登录" : "注册"}
            </Button>
          </form>
          <div className="mt-4 text-center text-sm text-muted-foreground">
            {mode === "login" ? (
              <button className="underline" onClick={() => setMode("register")}>
                没有账号？注册新团队
              </button>
            ) : (
              <button className="underline" onClick={() => setMode("login")}>
                已有账号？去登录
              </button>
            )}
          </div>
          {mode === "login" && (
            <div className="mt-2 text-center text-sm">
              <Link to="/forgot-password" className="text-muted-foreground underline">忘记密码？</Link>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
