import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get("token") ?? "";

  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError("两次输入的密码不一致");
      return;
    }
    setLoading(true);
    try {
      await api("/auth/password/reset", { method: "POST", body: { token, password } });
      setDone(true);
      setTimeout(() => navigate("/login"), 2000);
    } catch (err) {
      setError(err instanceof Error ? err.message : "重置失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/30 px-4">
      <Card className="w-full max-w-md">
        <CardHeader><CardTitle>重置密码</CardTitle></CardHeader>
        <CardContent>
          {!token ? (
            <p className="text-sm text-destructive">链接无效：缺少令牌。</p>
          ) : done ? (
            <div className="space-y-4 text-sm">
              <p className="text-emerald-600">密码已重置，原有会话已全部下线，正在跳转到登录…</p>
              <Link to="/login" className="text-primary underline">立即登录</Link>
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="pwd">新密码</Label>
                <Input id="pwd" type="password" value={password}
                  onChange={(e) => setPassword(e.target.value)} required />
                <p className="text-xs text-muted-foreground">至少10位，需包含字母、数字和特殊字符</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirm">确认新密码</Label>
                <Input id="confirm" type="password" value={confirm}
                  onChange={(e) => setConfirm(e.target.value)} required />
              </div>
              {error && <p className="text-sm text-destructive">{error}</p>}
              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? "提交中..." : "重置密码"}
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
