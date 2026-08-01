import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import api, { saveAuth } from '../lib/api'
import { Button } from '../components/ui/button'
import { Input, Label } from '../components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'

export default function AcceptInvite() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const navigate = useNavigate()
  const [info, setInfo] = useState(null)
  const [form, setForm] = useState({ name: '', password: '' })
  const [error, setError] = useState('')

  useEffect(() => {
    if (token) {
      api.get(`/auth/invite-info?token=${token}`)
        .then((res) => setInfo(res.data))
        .catch((err) => setError(err.response?.data?.message || '邀请链接无效或已过期'))
    }
  }, [token])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      const { data } = await api.post('/auth/accept-invite', { token, ...form })
      saveAuth(data)
      navigate('/')
    } catch (err) {
      setError(err.response?.data?.message || '接受邀请失败')
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>接受团队邀请</CardTitle>
        </CardHeader>
        <CardContent>
          {info ? (
            <form onSubmit={submit} className="space-y-4">
              <p className="text-sm text-muted-foreground">
                邀请加入 <b>{info.teamName}</b>（{info.email}）
              </p>
              <div className="space-y-2">
                <Label>姓名</Label>
                <Input value={form.name} required
                  onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>设置密码</Label>
                <Input type="password" value={form.password} required
                  placeholder="至少10位，含字母+数字+特殊字符"
                  onChange={(e) => setForm({ ...form, password: e.target.value })} />
              </div>
              {error && <p className="text-sm text-destructive">{error}</p>}
              <Button className="w-full">加入团队</Button>
            </form>
          ) : (
            <p className="text-sm text-destructive">{error || '加载中…'}</p>
          )}
          <p className="mt-4 text-sm">
            <Link to="/login" className="text-primary hover:underline">返回登录</Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
