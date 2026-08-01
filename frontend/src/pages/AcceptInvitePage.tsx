import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "@/lib/api";
import { useAuth } from "@/context/AuthContext";
import type { TokenResponse } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function AcceptInvitePage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { applyTokens } = useAuth();
  const token = params.get("token") ?? "";

  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const t = await api<TokenResponse>("/invitations/accept", {
        method: "POST",
        body: { token, name, password },
      });
      // Accepting logs the new member straight in.
      applyTokens(t);
      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "接受邀请失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/30 px-4">
      <Card className="w-full max-w-md">
        <CardHeader><CardTitle>接受团队邀请</CardTitle></CardHeader>
        <CardContent>
          {!token ? (
            <p className="text-sm text-destructive">链接无效：缺少令牌。</p>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <p className="text-sm text-muted-foreground">
                设置你的姓名与密码以加入团队。
              </p>
              <div className="space-y-2">
                <Label htmlFor="name">姓名</Label>
                <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="pwd">密码</Label>
                <Input id="pwd" type="password" value={password}
                  onChange={(e) => setPassword(e.target.value)} required />
                <p className="text-xs text-muted-foreground">至少10位，需包含字母、数字和特殊字符</p>
              </div>
              {error && <p className="text-sm text-destructive">{error}</p>}
              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? "提交中..." : "加入团队"}
              </Button>
              <div className="text-center text-sm">
                <Link to="/login" className="text-muted-foreground underline">已有账号？去登录</Link>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
